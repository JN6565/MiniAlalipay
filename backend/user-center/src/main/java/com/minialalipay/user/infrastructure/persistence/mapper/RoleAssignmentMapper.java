package com.minialalipay.user.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 角色授权 MyBatis Mapper 接口。
 *
 * <p>负责 {@code role_assignment} 表的查询操作。角色授权只读，不在此维护角色数据。</p>
 */
@Mapper
public interface RoleAssignmentMapper {

    /**
     * 查询用户拥有的全部角色代码。
     *
     * @param userId 用户 ID（26 位字符，USR 前缀）
     * @return 角色代码列表，无授权时返回空列表
     */
    List<String> selectRolesByUserId(@Param("userId") String userId);
}
