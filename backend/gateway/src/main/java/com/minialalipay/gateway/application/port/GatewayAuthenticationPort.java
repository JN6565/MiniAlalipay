package com.minialalipay.gateway.application.port;

import com.minialalipay.gateway.application.security.GatewayAuthContext;
import reactor.core.publisher.Mono;

/**
 * 网关会话认证端口。
 *
 * <p>实现只能根据服务端可信配置或用户中心公开契约建立认证上下文，
 * 不得从客户端提交的用户、角色或账户字段推导身份。</p>
 */
public interface GatewayAuthenticationPort {

    /**
     * 校验会话令牌。
     *
     * @param token 不含 Bearer 前缀的会话令牌
     * @return 有效令牌对应的认证上下文；无效令牌返回空 Mono
     */
    Mono<GatewayAuthContext> authenticate(String token);
}
