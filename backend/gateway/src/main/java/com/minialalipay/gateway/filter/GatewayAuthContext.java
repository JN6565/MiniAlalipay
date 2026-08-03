package com.minialalipay.gateway.filter;

import java.util.Set;

/**
 * 网关认证上下文，由 {@link AuthenticationGlobalFilter} 写入 Reactor Context。
 *
 * <p>当前为阶段二 Stub 实现，后续将切换为用户中心真实会话校验。</p>
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
