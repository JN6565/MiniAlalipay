package com.minialalipay.user.interfaces.dto.auth;

import java.util.Set;

/**
 * 当前身份响应 DTO。
 *
 * <p>返回 B 端登录后的当前身份：用户 ID、展示名与角色集合，供前端权限模型填充。
 * 不返回手机号、密码哈希等敏感字段。</p>
 *
 * @param userId      用户 ID
 * @param displayName 展示名（真实姓名优先）
 * @param roles       用户实际拥有的角色集合
 */
public record CurrentIdentityResponseDTO(String userId, String displayName, Set<String> roles) {
}
