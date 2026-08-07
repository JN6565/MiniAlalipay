package com.minialalipay.user.infrastructure.persistence.po;

import java.time.Instant;

public class FriendRequestPO {
    private String requestId;
    private String fromUserId;
    private String toUserId;
    private String status;
    private String message;
    private Instant createdAt;
    private Instant updatedAt;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getFromUserId() { return fromUserId; }
    public void setFromUserId(String fromUserId) { this.fromUserId = fromUserId; }
    public String getToUserId() { return toUserId; }
    public void setToUserId(String toUserId) { this.toUserId = toUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
