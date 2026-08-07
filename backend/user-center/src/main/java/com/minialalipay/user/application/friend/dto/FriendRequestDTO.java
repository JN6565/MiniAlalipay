package com.minialalipay.user.application.friend.dto;

import java.time.Instant;

public record FriendRequestDTO(
        String requestId,
        String fromUserId,
        String fromUserName,
        String toUserId,
        String status,
        String message,
        Instant createdAt
) {
}
