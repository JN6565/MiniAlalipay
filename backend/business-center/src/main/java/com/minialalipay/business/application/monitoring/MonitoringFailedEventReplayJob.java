package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringOutboxPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 失败监控事件历史回放任务。
 *
 * <p>任务只从 Inbox 找到达到退避时间的失败 ID，再从已发布 Outbox 读取原始脱敏载荷并追加到 Stream。
 * 不修改业务事实；重复追加由 Inbox 的事件唯一键和投影幂等约束消除。</p>
 */
@Service
@ConditionalOnProperty(name = "minialalipay.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringFailedEventReplayJob {
    private final MonitoringEventStore inbox;
    private final MonitoringOutboxPort outbox;
    private final MonitoringStreamPort stream;
    private final int batchSize;

    public MonitoringFailedEventReplayJob(MonitoringEventStore inbox, MonitoringOutboxPort outbox,
                                          MonitoringStreamPort stream,
                                          @Value("${minialalipay.monitoring.replay.batch-size:100}") int batchSize) {
        if (batchSize < 1 || batchSize > 500) throw new IllegalArgumentException("监控回放批次必须在 1 到 500 之间");
        this.inbox = inbox;
        this.outbox = outbox;
        this.stream = stream;
        this.batchSize = batchSize;
    }

    /** 周期性补投递已发布但尚未完成投影的历史事件。 */
    @Scheduled(fixedDelayString = "${minialalipay.monitoring.replay.fixed-delay-ms:30000}",
            initialDelayString = "${minialalipay.monitoring.replay.initial-delay-ms:10000}")
    public void replayFailedEvents() {
        List<String> ids = inbox.findRetryableEventIds(Instant.now(), batchSize);
        for (MonitoringOutboxPort.PendingMonitoringEvent event : outbox.findPublishedEvents(ids)) {
            try {
                stream.append(event.event());
            } catch (RuntimeException ignored) {
                // 下次调度继续回放；不把投递失败误标记为投影成功。
            }
        }
    }
}
