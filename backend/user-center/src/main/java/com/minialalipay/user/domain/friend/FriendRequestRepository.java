package com.minialalipay.user.domain.friend;

import java.util.List;
import java.util.Optional;

public interface FriendRequestRepository {
    void save(FriendRequest request);
    void update(FriendRequest request);
    Optional<FriendRequest> findById(String requestId);
    Optional<FriendRequest> findPendingBetween(String fromUserId, String toUserId);
    List<FriendRequest> findPendingByToUserId(String toUserId, int limit);
}
