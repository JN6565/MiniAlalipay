package com.minialalipay.user.application.auth.dto;

import java.util.Set;

/**
 * 当前身份查询结果，供 B 端当前身份接口消费。
 *
 * @param userId      用户 ID
 * @param displayName 展示名（真实姓名优先，缺省用昵称）
 * @param roles       用户实际拥有的角色集合（含默认角色）
 */
public record CurrentIdentity(String userId, String displayName, Set<String> roles) {
}
