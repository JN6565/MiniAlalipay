package com.minialalipay.user.infrastructure.persistence.po;

import java.time.Instant;

public class FriendPO {
    private String userId;
    private String friendUserId;
    private String alias;
    private Instant createdAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFriendUserId() { return friendUserId; }
    public void setFriendUserId(String friendUserId) { this.friendUserId = friendUserId; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
