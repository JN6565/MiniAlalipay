package com.minialalipay.user.interfaces.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** 网关调用的会话校验请求，令牌不得记录到日志。 */
public record SessionIntrospectionRequestDTO(@NotBlank String accessToken) {
}
