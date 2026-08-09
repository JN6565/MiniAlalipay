package com.minialalipay.business.application.monitoring;

import java.util.List;

/**
 * 监控 Redis Stream 的技术端口。
 *
 * <p>流只承载脱敏后的领域事件。消费者的已处理位置存放在 {@code metrics_db}，因此 Redis 投递与
 * 数据库提交之间发生故障时允许重复读取，由 Inbox 去重收敛为一次投影。</p>
 */
public interface MonitoringStreamPort {
    /** 追加一条已持久化的监控事件；正常返回即代表 Redis 已接受消息。 */
    void append(MonitoringEvent event);

    /** 从严格大于 cursor 的位置读取最多 limit 条消息。 */
    List<StreamMessage> readAfter(String cursor, int limit);

    /** Redis 消息 ID 与反序列化后的监控事件。 */
    record StreamMessage(String cursor, MonitoringEvent event) { }
}
