package com.minialalipay.business.application.monitoring;

import java.time.Instant;
import java.util.List;

/**
 * 监控 Inbox 与投影存储端口。
 *
 * <p>实现必须在 metrics_db 同一事务内完成 claim、投影和 complete；失败时保留可重试的 Inbox 状态，
 * 不得调用账户中心或修改业务资金事实。</p>
 */
public interface MonitoringEventStore {
    /** 查询达到退避时间且未超过最大次数的失败事件，供历史回放任务使用。 */
    default List<String> findRetryableEventIds(Instant now, int limit) { return List.of(); }
    /**
     * 原子领取事件。
     *
     * <p>领取结果必须区分已完成与暂不可领取：前者可以安全推进 Stream 游标，后者必须保留游标等待
     * 失败重试窗口或并发消费者完成，避免把暂时失败的事件永久跳过。</p>
     */
    InboxClaimResult claim(String consumerName, String eventId);

    /**
     * Inbox 领取结果。
     *
     * <p>{@link #CLAIMED} 表示当前消费者已取得投影权；{@link #ALREADY_DONE} 是可安全跳过的终态；
     * {@link #RETRY_LATER} 表示失败退避中或其他实例仍在处理，Stream 游标不得推进。</p>
     */
    enum InboxClaimResult {
        /** 当前消费者已原子领取事件，允许执行投影。 */
        CLAIMED,
        /** 事件已完成投影，属于幂等重读，可安全推进游标。 */
        ALREADY_DONE,
        /** 事件尚不可重试或正由其他实例处理，必须保留当前游标。 */
        RETRY_LATER
    }

    /** 将事件投影到指标、告警或质量查询模型。 */
    void project(MonitoringEvent event);

    /** 标记 Inbox 消费完成。 */
    void complete(String consumerName, String eventId);

    /** 标记可重试失败并保留错误信息。 */
    void fail(String consumerName, String eventId, String reason);

    /** 将不符合事件契约的消息隔离。 */
    void quarantine(MonitoringEvent event, String reason);

    /** 返回指定流消费者最后一个已完成持久化的 Redis Stream 消息游标。 */
    default String currentStreamCursor(String consumerName) { return "0-0"; }

    /**
     * 以旧游标 CAS 推进消费进度。
     *
     * <p>必须与 Inbox 终态写入处于同一 {@code metrics_db} 本地事务，进程在提交前崩溃时会重读消息，
     * 由 Inbox 去重保证不会重复投影。</p>
     */
    default boolean advanceStreamCursor(String consumerName, String expectedCursor, String nextCursor) { return true; }
}
