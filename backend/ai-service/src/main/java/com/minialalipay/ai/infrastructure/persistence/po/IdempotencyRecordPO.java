package com.minialalipay.ai.infrastructure.persistence.po;

/**
 * {@code agent_db.idempotency_record} 持久化对象。
 */
public class IdempotencyRecordPO {
    private String recordId;
    private String principalKey;
    private String apiScope;
    private String idempotencyKey;
    private byte[] requestDigest;
    private String resourceType;
    private String resourceId;
    private String responseJson;
    private String status;
    private java.time.Instant expiresAt;
    private java.time.Instant createdAt;
    private java.time.Instant updatedAt;

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getPrincipalKey() { return principalKey; }
    public void setPrincipalKey(String principalKey) { this.principalKey = principalKey; }
    public String getApiScope() { return apiScope; }
    public void setApiScope(String apiScope) { this.apiScope = apiScope; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public byte[] getRequestDigest() { return requestDigest; }
    public void setRequestDigest(byte[] requestDigest) { this.requestDigest = requestDigest; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public java.time.Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(java.time.Instant expiresAt) { this.expiresAt = expiresAt; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }
}
