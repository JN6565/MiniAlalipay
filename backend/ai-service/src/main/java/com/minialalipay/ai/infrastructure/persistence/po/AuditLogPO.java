package com.minialalipay.ai.infrastructure.persistence.po;

/**
 * {@code agent_db.audit_log} 持久化对象。
 */
public class AuditLogPO {
    private Long auditId;
    private String actorType;
    private String actorId;
    private String action;
    private String targetType;
    private String targetId;
    private String resultCode;
    private String traceId;
    private String detailJson;
    private java.time.Instant occurredAt;

    public Long getAuditId() { return auditId; }
    public void setAuditId(Long auditId) { this.auditId = auditId; }
    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public java.time.Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(java.time.Instant occurredAt) { this.occurredAt = occurredAt; }
}
