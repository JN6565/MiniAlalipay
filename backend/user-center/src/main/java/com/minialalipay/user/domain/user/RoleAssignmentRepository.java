package com.minialalipay.user.domain.user;

import java.util.Set;

/**
 * 角色授权仓储端口。
 *
 * <p>负责查询用户在 {@code role_assignment} 表中授权的角色代码，供会话校验、当前身份和
 * B 端角色门禁消费。角色事实只由用户中心持有，其他服务仅消费网关注入的可信角色头。</p>
 */
public interface RoleAssignmentRepository {

    /**
     * 查询用户在角色授权表中拥有的角色代码集合。
     *
     * @param userId 用户 ID（ULID 格式，26 位字符）
     * @return 角色代码集合；无授权时返回空集合（由调用方决定默认角色）
     */
    Set<String> findRolesByUserId(String userId);
}
