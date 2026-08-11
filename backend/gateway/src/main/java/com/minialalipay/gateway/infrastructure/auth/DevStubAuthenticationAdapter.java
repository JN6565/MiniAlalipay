package com.minialalipay.gateway.infrastructure.auth;

import com.minialalipay.gateway.application.port.GatewayAuthenticationPort;
import com.minialalipay.gateway.application.security.GatewayAuthContext;
import com.minialalipay.gateway.infrastructure.config.GatewayAuthenticationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本地演示鉴权桩（dev Stub）。
 *
 * <p>仅当 {@code gateway.authentication.stub.enabled=true}（例如本地演示环境）时激活，
 * 用于在没有可登录演示账号口令的情况下提供受控身份，使 B 端本地演示可直接打通。
 * 生产环境必须保持关闭，交由 {@link UserCenterAuthenticationAdapter} 回源用户中心校验会话。</p>
 *
 * <p>受控语义：
 * <ul>
 *   <li>只有令牌与 {@code gateway.authentication.stub.token} 完全一致才放行，避免任意令牌获得运维身份</li>
 *   <li>主体与角色来自受控配置（{@code stub.principal-id}/{@code stub.roles}），不读取客户端身份头</li>
 *   <li>角色经过身份头清洗与运维门禁后由全局过滤器注入下游</li>
 * </ul>
 * </p>
 */
@Component
@ConditionalOnProperty(name = "gateway.authentication.stub.enabled", havingValue = "true")
public final class DevStubAuthenticationAdapter implements GatewayAuthenticationPort {

    private final String stubToken;
    private final String principalId;
    private final Set<String> roles;

    @Autowired
    public DevStubAuthenticationAdapter(GatewayAuthenticationProperties properties) {
        this(properties.getStub().getToken(), properties.getStub().getPrincipalId(), properties.getStub().getRoles());
    }

    public DevStubAuthenticationAdapter(String stubToken, String principalId, String stubRoles) {
        this.stubToken = stubToken;
        this.principalId = principalId;
        this.roles = Arrays.stream(stubRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Mono<GatewayAuthContext> authenticate(String token) {
        return stubToken.equals(token)
                ? Mono.just(new GatewayAuthContext(principalId, roles))
                : Mono.empty();
    }
}
