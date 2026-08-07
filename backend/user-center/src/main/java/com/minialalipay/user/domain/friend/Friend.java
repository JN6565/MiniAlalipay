package com.minialalipay.user.domain.friend;

import java.time.Instant;

public class Friend {
    private String userId;
    private String friendUserId;
    private String alias;
    private Instant createdAt;

    public Friend(String userId, String friendUserId, Instant createdAt) {
        this.userId = userId;
        this.friendUserId = friendUserId;
        this.createdAt = createdAt;
    }

    public Friend(String userId, String friendUserId, String alias, Instant createdAt) {
        this.userId = userId;
        this.friendUserId = friendUserId;
        this.alias = alias;
        this.createdAt = createdAt;
    }

    public void updateAlias(String alias) {
        this.alias = alias;
    }

    public String getUserId() { return userId; }
    public String getFriendUserId() { return friendUserId; }
    public String getAlias() { return alias; }
    public Instant getCreatedAt() { return createdAt; }
}
