package com.minialalipay.user.domain.friend;

import java.util.List;
import java.util.Optional;

public interface FriendRepository {
    void save(Friend friend);
    void delete(String userId, String friendUserId);
    Optional<Friend> findByUserAndFriend(String userId, String friendUserId);
    List<Friend> findByUserId(String userId, int limit);
    boolean areFriends(String userId1, String userId2);
}
