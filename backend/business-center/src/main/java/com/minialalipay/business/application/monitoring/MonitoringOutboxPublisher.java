package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringOutboxPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Instant;

/**
 * 将业务 Outbox 可靠投递到监控 Redis Stream。
 *
 * <p>先写入 Stream，得到 Redis 确认后才将 Outbox 标为 {@code PUBLISHED}。如果确认后进程崩溃，
 * 后续会重复投递同一 eventId；监控侧 Inbox 的唯一键保证指标最多投影一次。</p>
 */
@Service
@ConditionalOnProperty(name = "minialalipay.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringOutboxPublisher {
    private final MonitoringOutboxPort outbox;
    private final MonitoringStreamPort stream;
    private final int batchSize;

    public MonitoringOutboxPublisher(MonitoringOutboxPort outbox, MonitoringStreamPort stream,
                                     @Value("${minialalipay.monitoring.outbox.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.stream = stream;
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("监控 Outbox 批次必须在 1 到 500 之间");
        this.batchSize = batchSize;
    }

    /** 定时投递当前到期的 Outbox 事件；单条失败不阻塞同批其他事件。 */
    @Scheduled(fixedDelayString = "${minialalipay.monitoring.outbox.fixed-delay-ms:1000}",
            initialDelayString = "${minialalipay.monitoring.outbox.initial-delay-ms:3000}")
    public void publishReadyEvents() {
        Instant now = Instant.now();
        for (MonitoringOutboxPort.PendingMonitoringEvent pending : outbox.findReadyEvents(now, batchSize)) {
            try {
                stream.append(pending.event());
                outbox.markPublished(pending.event().eventId(), Instant.now());
            } catch (RuntimeException ex) {
                outbox.scheduleRetry(pending.event().eventId(), Instant.now().plusSeconds(retryDelaySeconds(pending.retryCount())));
            }
        }
    }

    private static long retryDelaySeconds(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(300L, 5L * (1L << exponent));
    }
}
