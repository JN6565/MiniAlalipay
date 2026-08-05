package com.minialalipay.ai.infrastructure.persistence.po;

/**
 * {@code agent_db.outbox_event} 持久化对象。
 */
public class OutboxEventPO {
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private Long aggregateVersion;
    private String eventType;
    private Integer eventVersion;
    private String businessType;
    private String sourceType;
    private String sourceOrderId;
    private String fundingSource;
    private String transactionId;
    private String producer;
    private String accountId;
    private String merchantAccountId;
    private byte[] userIdHash;
    private String traceId;
    private java.time.Instant occurredAt;
    private String payload;
    private String status;
    private Integer retryCount;
    private java.time.Instant nextRetryAt;
    private java.time.Instant createdAt;
    private java.time.Instant publishedAt;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public Long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(Long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Integer getEventVersion() { return eventVersion; }
    public void setEventVersion(Integer eventVersion) { this.eventVersion = eventVersion; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceOrderId() { return sourceOrderId; }
    public void setSourceOrderId(String sourceOrderId) { this.sourceOrderId = sourceOrderId; }
    public String getFundingSource() { return fundingSource; }
    public void setFundingSource(String fundingSource) { this.fundingSource = fundingSource; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getProducer() { return producer; }
    public void setProducer(String producer) { this.producer = producer; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getMerchantAccountId() { return merchantAccountId; }
    public void setMerchantAccountId(String merchantAccountId) { this.merchantAccountId = merchantAccountId; }
    public byte[] getUserIdHash() { return userIdHash; }
    public void setUserIdHash(byte[] userIdHash) { this.userIdHash = userIdHash; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public java.time.Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(java.time.Instant occurredAt) { this.occurredAt = occurredAt; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public java.time.Instant getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(java.time.Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
    public java.time.Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(java.time.Instant publishedAt) { this.publishedAt = publishedAt; }
}
