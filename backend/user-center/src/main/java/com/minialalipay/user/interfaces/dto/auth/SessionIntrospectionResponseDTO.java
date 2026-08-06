package com.minialalipay.user.interfaces.dto.auth;

import java.util.Set;

/** 会话校验结果；无效会话仅返回 active=false。 */
public record SessionIntrospectionResponseDTO(boolean active, String userId, Set<String> roles) {
}
