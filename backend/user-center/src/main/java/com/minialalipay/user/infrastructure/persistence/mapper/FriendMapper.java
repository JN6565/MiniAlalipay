package com.minialalipay.user.infrastructure.persistence.mapper;

import com.minialalipay.user.infrastructure.persistence.po.FriendPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface FriendMapper {
    void insert(FriendPO po);
    void delete(@Param("userId") String userId, @Param("friendUserId") String friendUserId);
    Optional<FriendPO> selectByUserAndFriend(@Param("userId") String userId, @Param("friendUserId") String friendUserId);
    List<FriendPO> selectByUserId(@Param("userId") String userId, @Param("limit") int limit);
    int countByUserAndFriend(@Param("userId1") String userId1, @Param("userId2") String userId2);
}
