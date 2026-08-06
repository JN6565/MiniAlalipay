package com.minialalipay.business.domain.monitoring;

import java.time.Instant;
import java.util.Objects;

/**
 * 由监控事件驱动的运营告警聚合。
 *
 * <p>告警处置只影响运营投影和审计信息，不能反向决定交易或账户资金状态。</p>
 */
public final class Alert {
    private final String alertId;
    private final String alertType;
    private final String severity;
    private AlertStatus status;
    private String operatorId;
    private String lastReason;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /** 创建一个开放告警。 */
    public static Alert open(String alertId, String alertType, String severity, Instant now) {
        return new Alert(alertId, alertType, severity, AlertStatus.OPEN, null, null, 0L, now, now);
    }

    /** 从持久化事实重建告警。 */
    public Alert(String alertId, String alertType, String severity, AlertStatus status, String operatorId,
                 String lastReason, long version, Instant createdAt, Instant updatedAt) {
        this.alertId = required(alertId, "告警 ID");
        this.alertType = required(alertType, "告警类型");
        this.severity = required(severity, "告警级别");
        this.status = Objects.requireNonNull(status, "告警状态不能为空");
        this.operatorId = operatorId;
        this.lastReason = lastReason;
        if (version < 0) throw new IllegalArgumentException("告警版本不得为负数");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /** 确认开放告警；同一操作人重试保持幂等。 */
    public void acknowledge(String actorId, long expectedVersion, String reason, Instant now) {
        checkVersion(expectedVersion);
        String validActorId = required(actorId, "操作人 ID");
        if (status == AlertStatus.ACKNOWLEDGED && validActorId.equals(operatorId)) return;
        if (status != AlertStatus.OPEN) throw new IllegalStateException("告警当前不可确认");
        operatorId = validActorId;
        lastReason = required(reason, "确认理由");
        status = AlertStatus.ACKNOWLEDGED;
        advance(now);
    }

    /** 将已确认告警标记为恢复。 */
    public void resolve(String actorId, long expectedVersion, String reason, Instant now) {
        checkOperator(actorId, expectedVersion);
        if (status != AlertStatus.ACKNOWLEDGED) throw new IllegalStateException("告警当前不可恢复");
        lastReason = required(reason, "恢复理由");
        status = AlertStatus.RESOLVED;
        advance(now);
    }

    /** 关闭已恢复告警。 */
    public void close(String actorId, long expectedVersion, String reason, Instant now) {
        checkOperator(actorId, expectedVersion);
        if (status != AlertStatus.RESOLVED) throw new IllegalStateException("告警当前不可关闭");
        lastReason = required(reason, "关闭理由");
        status = AlertStatus.CLOSED;
        advance(now);
    }

    /** 由新的监控事件重开已恢复告警。 */
    public void reopen(long expectedVersion, String reason, Instant now) {
        checkVersion(expectedVersion);
        if (status != AlertStatus.RESOLVED) throw new IllegalStateException("仅已恢复告警可以重开");
        lastReason = required(reason, "重开理由");
        status = AlertStatus.OPEN;
        operatorId = null;
        advance(now);
    }

    private void checkOperator(String actorId, long expectedVersion) {
        checkVersion(expectedVersion);
        if (!Objects.equals(operatorId, actorId)) throw new IllegalStateException("仅告警处置人可以执行该操作");
    }
    private void checkVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("告警版本已经变化");
    }
    private void advance(Instant now) { version++; updatedAt = now; }
    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getAlertId() { return alertId; }
    public String getAlertType() { return alertType; }
    public String getSeverity() { return severity; }
    public AlertStatus getStatus() { return status; }
    public String getOperatorId() { return operatorId; }
    public String getLastReason() { return lastReason; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
