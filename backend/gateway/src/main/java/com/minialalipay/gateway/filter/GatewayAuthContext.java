package com.minialalipay.gateway.filter;

import java.util.Set;

/**
 * 网关认证上下文，由 {@link AuthenticationGlobalFilter} 写入 Reactor Context。
 *
 * <p>由 {@link com.minialalipay.gateway.auth.UserCenterAuthenticationAdapter}
 * 通过用户中心 {@code /internal/v1/auth/sessions/introspect} 校验会话令牌后填充。</p>
 *
 * @param principalId 认证主体标识
 * @param roles       主体拥有的角色集合
 */
public record GatewayAuthContext(String principalId, Set<String> roles) {

    /** Reactor Context 中认证上下文的键。 */
    public static final String CONTEXT_KEY = "gateway.authContext";

    /** 匿名或未认证请求的角色标记。 */
    public static final String ROLE_ANONYMOUS = "ANONYMOUS";
}
