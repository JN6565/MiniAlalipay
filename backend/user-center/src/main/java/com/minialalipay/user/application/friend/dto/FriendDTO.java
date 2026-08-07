package com.minialalipay.user.application.friend.dto;

import java.time.Instant;

public record FriendDTO(
        String friendUserId,
        String friendName,
        String accountNumber,
        String alias,
        Instant createdAt
) {
}
