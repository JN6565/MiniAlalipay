package com.minialalipay.business.application.port;

import com.minialalipay.business.application.monitoring.MonitoringEvent;

import java.time.Instant;
import java.util.List;

/**
 * 监控 Outbox 发布端口。
 *
 * <p>只读取和更新 {@code business_db.outbox_event} 的投递状态，不得修改交易、账户或账本事实。
 * 事件已由资金交易本地事务提交；端口负责让后续异步投递可恢复。</p>
 */
public interface MonitoringOutboxPort {
    /** 查询当前可投递的事件，调用方必须在投递成功后显式标记已发布。 */
    List<PendingMonitoringEvent> findReadyEvents(Instant now, int limit);

    /** Redis Stream 已确认写入后，将对应 Outbox 标为 PUBLISHED。 */
    void markPublished(String eventId, Instant publishedAt);

    /** 投递异常时增加重试次数并设置下一次可投递时间。 */
    void scheduleRetry(String eventId, Instant nextRetryAt);

    /** 按失败 Inbox 的事件 ID 读取已发布 Outbox，回放时不得读取未发布或载荷不完整的事件。 */
    default List<PendingMonitoringEvent> findPublishedEvents(List<String> eventIds) { return List.of(); }

    /** 已脱敏且可直接转换为监控消费契约的 Outbox 事件。 */
    record PendingMonitoringEvent(MonitoringEvent event, int retryCount) { }
}
