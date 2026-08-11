package com.minialalipay.gateway.application.security;

import java.util.Set;

/**
 * 网关认证上下文，由接口层认证过滤器写入 Reactor Context。
 *
 * <p>认证端口完成会话校验后填充该上下文；接口层只能使用这里的可信主体和角色，
 * 不能使用客户端提交的身份头建立认证结果。</p>
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
