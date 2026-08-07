package com.minialalipay.user.domain.friend;

import java.time.Instant;

public class FriendRequest {
    private String requestId;
    private String fromUserId;
    private String toUserId;
    private FriendRequestStatus status;
    private String message;
    private Instant createdAt;
    private Instant updatedAt;

    public FriendRequest(String requestId, String fromUserId, String toUserId, String message, Instant now) {
        this.requestId = requestId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.status = FriendRequestStatus.PENDING;
        this.message = message;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public FriendRequest(String requestId, String fromUserId, String toUserId,
                         FriendRequestStatus status, String message, Instant createdAt, Instant updatedAt) {
        this.requestId = requestId;
        this.fromUserId = fromUserId;
        this.toUserId = toUserId;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void accept(Instant now) {
        this.status = FriendRequestStatus.ACCEPTED;
        this.updatedAt = now;
    }

    public void reject(Instant now) {
        this.status = FriendRequestStatus.REJECTED;
        this.updatedAt = now;
    }

    public boolean isPending() {
        return this.status == FriendRequestStatus.PENDING;
    }

    public String getRequestId() { return requestId; }
    public String getFromUserId() { return fromUserId; }
    public String getToUserId() { return toUserId; }
    public FriendRequestStatus getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
