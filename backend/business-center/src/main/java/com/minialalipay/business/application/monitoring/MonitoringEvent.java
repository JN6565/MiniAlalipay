package com.minialalipay.business.application.monitoring;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 监控事件消费边界对象。
 *
 * <p>消息载荷只允许使用已经脱敏的结构化字段；支付密码、确认令牌和原始二维码令牌不得进入载荷。</p>
 */
public record MonitoringEvent(String eventId, String eventType, int version, Instant occurredAt,
                              String traceId, Map<String, String> attributes) {
    /** 事件契约当前支持的版本。 */
    public static final int SUPPORTED_VERSION = 1;

    /** 创建事件并复制属性，防止监听器修改消息事实。 */
    public MonitoringEvent {
        require(eventId, "事件 ID");
        require(eventType, "事件类型");
        if (version < 1) throw new IllegalArgumentException("事件版本必须为正数");
        Objects.requireNonNull(occurredAt, "事件时间不能为空");
        require(traceId, "Trace ID");
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "事件属性不能为空"));
    }

    private static void require(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
    }
}
