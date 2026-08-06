package com.minialalipay.business.application.monitoring;

import java.util.Map;
import java.util.Set;

/**
 * 幂等运营事件消费者。
 *
 * <p>支持风险决策、告警、数据质量、交易状态和对账差异事件。未知版本或缺失字段进入隔离，
 * 重复投递直接返回幂等结果；任何消费异常只返回可重试失败，不推断交易成功。</p>
 */
public final class MonitoringEventConsumer {
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "risk.decision.created",
            "alert.status.changed",
            "data_quality.check.completed",
            "transaction.status.changed",
            "reconciliation.diff.detected");

    private final String consumerName;
    private final MonitoringEventStore store;

    /**
     * @param consumerName Inbox 消费者名称，必须稳定以实现事件去重
     * @param store metrics_db 投影端口
     */
    public MonitoringEventConsumer(String consumerName, MonitoringEventStore store) {
        if (consumerName == null || consumerName.isBlank()) throw new IllegalArgumentException("消费者名称不能为空");
        this.consumerName = consumerName;
        this.store = java.util.Objects.requireNonNull(store, "监控事件存储不能为空");
    }

    /**
     * 消费一条事件。
     *
     * @param event 已通过消息反序列化的事件
     * @return 消费结果；调用方据此确认或重试消息
     */
    public EventConsumeResult consume(MonitoringEvent event) {
        if (event == null) return EventConsumeResult.QUARANTINED;
        if (!store.claim(consumerName, event.eventId())) return EventConsumeResult.DUPLICATE;
        String invalidReason = validate(event);
        if (invalidReason != null) {
            store.quarantine(event, invalidReason);
            return EventConsumeResult.QUARANTINED;
        }
        try {
            store.project(event);
            store.complete(consumerName, event.eventId());
            return EventConsumeResult.PROJECTED;
        } catch (RuntimeException ex) {
            store.fail(consumerName, event.eventId(), ex.getMessage() == null ? "投影失败" : ex.getMessage());
            return EventConsumeResult.RETRYABLE_FAILURE;
        }
    }

    private String validate(MonitoringEvent event) {
        if (event.version() != MonitoringEvent.SUPPORTED_VERSION) return "未知事件版本";
        if (!SUPPORTED_TYPES.contains(event.eventType())) return "未知事件类型";
        if (event.attributes().containsKey("paymentPassword") || event.attributes().containsKey("rawToken")
                || event.attributes().containsKey("confirmationToken")) return "事件包含禁止的敏感字段";
        String[] required = requiredFields(event.eventType());
        for (String field : required) {
            if (!hasText(event.attributes(), field)) return "事件缺少字段: " + field;
        }
        return null;
    }

    private static String[] requiredFields(String eventType) {
        return switch (eventType) {
            case "risk.decision.created" -> new String[]{"decisionId", "subjectId", "action"};
            case "alert.status.changed" -> new String[]{"alertId", "status"};
            case "data_quality.check.completed" -> new String[]{"resultId", "status"};
            case "transaction.status.changed" -> new String[]{"transactionId", "status"};
            case "reconciliation.diff.detected" -> new String[]{"transactionId", "diffType"};
            default -> new String[0];
        };
    }

    private static boolean hasText(Map<String, String> attributes, String key) {
        String value = attributes.get(key);
        return value != null && !value.isBlank();
    }
}
