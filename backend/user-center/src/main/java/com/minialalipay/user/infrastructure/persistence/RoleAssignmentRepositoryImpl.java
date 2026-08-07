package com.minialalipay.user.infrastructure.persistence;

import com.minialalipay.user.domain.user.RoleAssignmentRepository;
import com.minialalipay.user.infrastructure.persistence.mapper.RoleAssignmentMapper;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色授权仓储实现。
 *
 * <p>使用 MyBatis {@link RoleAssignmentMapper} 查询 {@code role_assignment} 表。
 * 只读角色事实，不修改角色数据。</p>
 */
@Repository
public class RoleAssignmentRepositoryImpl implements RoleAssignmentRepository {

    private final RoleAssignmentMapper roleAssignmentMapper;

    /** 构造注入角色授权 Mapper。 */
    public RoleAssignmentRepositoryImpl(RoleAssignmentMapper roleAssignmentMapper) {
        this.roleAssignmentMapper = roleAssignmentMapper;
    }

    @Override
    public Set<String> findRolesByUserId(String userId) {
        return roleAssignmentMapper.selectRolesByUserId(userId).stream()
                .collect(Collectors.toUnmodifiableSet());
    }
}
