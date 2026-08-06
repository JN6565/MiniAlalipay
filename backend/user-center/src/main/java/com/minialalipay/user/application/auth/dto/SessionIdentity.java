package com.minialalipay.user.application.auth.dto;

import java.util.Set;

/**
 * 会话解析结果：由会话令牌解析出的可信主体与角色集合。
 *
 * <p>用于网关内部会话校验，避免把客户端提交的身份字段当作解析结果。</p>
 *
 * @param userId 会话对应的用户 ID
 * @param roles  用户实际拥有的角色集合（含默认角色）
 */
public record SessionIdentity(String userId, Set<String> roles) {
}
