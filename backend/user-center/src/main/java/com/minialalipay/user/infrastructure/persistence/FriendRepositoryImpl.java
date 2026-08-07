package com.minialalipay.user.infrastructure.persistence;

import com.minialalipay.user.domain.friend.Friend;
import com.minialalipay.user.domain.friend.FriendRepository;
import com.minialalipay.user.infrastructure.persistence.mapper.FriendMapper;
import com.minialalipay.user.infrastructure.persistence.po.FriendPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class FriendRepositoryImpl implements FriendRepository {

    private final FriendMapper mapper;

    public FriendRepositoryImpl(FriendMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(Friend friend) {
        mapper.insert(toPO(friend));
    }

    @Override
    public void delete(String userId, String friendUserId) {
        mapper.delete(userId, friendUserId);
    }

    @Override
    public Optional<Friend> findByUserAndFriend(String userId, String friendUserId) {
        return mapper.selectByUserAndFriend(userId, friendUserId).map(this::toDomain);
    }

    @Override
    public List<Friend> findByUserId(String userId, int limit) {
        return mapper.selectByUserId(userId, limit).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public boolean areFriends(String userId1, String userId2) {
        return mapper.countByUserAndFriend(userId1, userId2) > 0;
    }

    private FriendPO toPO(Friend f) {
        FriendPO po = new FriendPO();
        po.setUserId(f.getUserId());
        po.setFriendUserId(f.getFriendUserId());
        po.setAlias(f.getAlias());
        po.setCreatedAt(f.getCreatedAt());
        return po;
    }

    private Friend toDomain(FriendPO po) {
        return new Friend(po.getUserId(), po.getFriendUserId(), po.getAlias(), po.getCreatedAt());
    }
}
