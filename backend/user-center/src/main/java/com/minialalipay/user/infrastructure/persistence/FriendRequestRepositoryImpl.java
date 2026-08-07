package com.minialalipay.user.infrastructure.persistence;

import com.minialalipay.user.domain.friend.FriendRequest;
import com.minialalipay.user.domain.friend.FriendRequestRepository;
import com.minialalipay.user.domain.friend.FriendRequestStatus;
import com.minialalipay.user.infrastructure.persistence.mapper.FriendRequestMapper;
import com.minialalipay.user.infrastructure.persistence.po.FriendRequestPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class FriendRequestRepositoryImpl implements FriendRequestRepository {

    private final FriendRequestMapper mapper;

    public FriendRequestRepositoryImpl(FriendRequestMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(FriendRequest request) {
        mapper.insert(toPO(request));
    }

    @Override
    public void update(FriendRequest request) {
        FriendRequestPO po = new FriendRequestPO();
        po.setRequestId(request.getRequestId());
        po.setStatus(request.getStatus().name());
        po.setUpdatedAt(request.getUpdatedAt());
        mapper.update(po);
    }

    @Override
    public Optional<FriendRequest> findById(String requestId) {
        return mapper.selectById(requestId).map(this::toDomain);
    }

    @Override
    public Optional<FriendRequest> findPendingBetween(String fromUserId, String toUserId) {
        return mapper.selectPendingBetween(fromUserId, toUserId).map(this::toDomain);
    }

    @Override
    public List<FriendRequest> findPendingByToUserId(String toUserId, int limit) {
        return mapper.selectPendingByToUserId(toUserId, limit).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private FriendRequestPO toPO(FriendRequest r) {
        FriendRequestPO po = new FriendRequestPO();
        po.setRequestId(r.getRequestId());
        po.setFromUserId(r.getFromUserId());
        po.setToUserId(r.getToUserId());
        po.setStatus(r.getStatus().name());
        po.setMessage(r.getMessage());
        po.setCreatedAt(r.getCreatedAt());
        po.setUpdatedAt(r.getUpdatedAt());
        return po;
    }

    private FriendRequest toDomain(FriendRequestPO po) {
        return new FriendRequest(
                po.getRequestId(),
                po.getFromUserId(),
                po.getToUserId(),
                FriendRequestStatus.valueOf(po.getStatus()),
                po.getMessage(),
                po.getCreatedAt(),
                po.getUpdatedAt()
        );
    }
}
