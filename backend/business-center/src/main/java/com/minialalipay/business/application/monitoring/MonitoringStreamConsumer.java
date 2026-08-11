package com.minialalipay.business.application.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

/**
 * 从监控 Redis Stream 恢复消费事件并驱动 metrics_db 投影。
 *
 * <p>检查点只能在 Inbox 已完成或事件已隔离后推进。可重试的投影失败保留原检查点，下一轮读取同一条
 * Stream 消息，直到 Inbox 恢复接管成功。</p>
 */
@Service
@ConditionalOnProperty(name = "minialalipay.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringStreamConsumer {
    private static final Logger log = LoggerFactory.getLogger(MonitoringStreamConsumer.class);
    private static final String CONSUMER_NAME = "ops-projection";

    private final MonitoringStreamPort stream;
    private final MonitoringEventConsumer consumer;
    private final MonitoringEventStore store;
    private final int batchSize;

    public MonitoringStreamConsumer(MonitoringStreamPort stream, MonitoringEventConsumer consumer,
                                    MonitoringEventStore store,
                                    @Value("${minialalipay.monitoring.consumer.batch-size:100}") int batchSize) {
        this.stream = stream;
        this.consumer = consumer;
        this.store = store;
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("监控消费批次必须在 1 到 500 之间");
        this.batchSize = batchSize;
    }

    /** 定时消费新消息，并在 metrics_db 本地事务中同时提交投影与游标。 */
    @Scheduled(fixedDelayString = "${minialalipay.monitoring.consumer.fixed-delay-ms:1000}",
            initialDelayString = "${minialalipay.monitoring.consumer.initial-delay-ms:3000}")
    @Transactional
    public void consumeAvailableEvents() {
        try {
            String cursor = store.currentStreamCursor(CONSUMER_NAME);
            for (MonitoringStreamPort.StreamMessage message : stream.readAfter(cursor, batchSize)) {
                EventConsumeResult result = consumer.consume(message.event());
                if (result == EventConsumeResult.RETRYABLE_FAILURE) return;
                if (!store.advanceStreamCursor(CONSUMER_NAME, cursor, message.cursor())) {
                    throw new IllegalStateException("监控事件消费游标并发冲突");
                }
                cursor = message.cursor();
            }
        } catch (ConcurrencyFailureException lockFailure) {
            // 多实例并发领取同一事件仍可能被 MySQL 判为死锁受害者：事务已被服务端整体回滚，
            // 投影与游标都未提交，下一轮调度从原游标重读即可，降级日志避免调度器 ERROR 噪音。
            log.warn("监控事件消费因锁竞争中断，保留游标待下轮重试: {}", lockFailure.getMessage());
        }
    }
}
