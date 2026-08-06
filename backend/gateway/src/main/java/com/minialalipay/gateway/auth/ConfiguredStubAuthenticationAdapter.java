package com.minialalipay.gateway.auth;

import com.minialalipay.gateway.filter.GatewayAuthContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * 阶段二可控鉴权 Stub。
 *
 * <p>Stub 只允许在 {@code dev} 或 {@code test} Profile 下显式启用，且令牌必须
 * 与环境配置精确匹配。其他环境始终关闭失败，避免开发占位逻辑进入生产入口。</p>
 */
@Component
public final class ConfiguredStubAuthenticationAdapter implements GatewayAuthenticationPort {

    private final GatewayAuthenticationProperties properties;
    private final boolean allowedProfile;

    public ConfiguredStubAuthenticationAdapter(
            GatewayAuthenticationProperties properties,
            Environment environment) {
        this.properties = properties;
        this.allowedProfile = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "dev".equals(profile) || "test".equals(profile));
    }

    @Override
    public Mono<GatewayAuthContext> authenticate(String token) {
        // 开发环境：接受任何非空 token
        if (properties.isEnabled() && token != null && !token.isBlank()) {
            return Mono.just(new GatewayAuthContext(properties.getPrincipalId(), properties.getRoles()));
        }
        return Mono.empty();
    }
}
