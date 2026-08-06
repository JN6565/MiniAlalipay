package com.minialalipay.business.application.monitoring;

/**
 * 监控 Inbox 与投影存储端口。
 *
 * <p>实现必须在 metrics_db 同一事务内完成 claim、投影和 complete；失败时保留可重试的 Inbox 状态，
 * 不得调用账户中心或修改业务资金事实。</p>
 */
public interface MonitoringEventStore {
    /**
     * 原子领取事件；返回 false 表示已完成消费或仍被其他消费者处理。
     *
     * <p>此前标记为失败且达到重试时间的 Inbox 记录必须允许重新领取，避免一次临时异常造成永久投影缺口。</p>
     */
    boolean claim(String consumerName, String eventId);

    /** 将事件投影到指标、告警或质量查询模型。 */
    void project(MonitoringEvent event);

    /** 标记 Inbox 消费完成。 */
    void complete(String consumerName, String eventId);

    /** 标记可重试失败并保留错误信息。 */
    void fail(String consumerName, String eventId, String reason);

    /** 将不符合事件契约的消息隔离。 */
    void quarantine(MonitoringEvent event, String reason);
}
