package com.minialalipay.user.infrastructure.persistence.mapper;

import com.minialalipay.user.infrastructure.persistence.po.FriendRequestPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FriendRequestMapper {
    void insert(FriendRequestPO po);
    void update(FriendRequestPO po);
    Optional<FriendRequestPO> selectById(@Param("requestId") String requestId);
    Optional<FriendRequestPO> selectPendingBetween(@Param("fromUserId") String fromUserId, @Param("toUserId") String toUserId);
    List<FriendRequestPO> selectPendingByToUserId(@Param("toUserId") String toUserId, @Param("limit") int limit);
}
