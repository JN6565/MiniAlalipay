package com.minialalipay.gateway.auth;

import com.minialalipay.gateway.filter.GatewayAuthContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.time.Duration;

/**
 * 用户中心真实会话认证适配器。
 *
 * <p>网关只信任用户中心返回的用户 ID，客户端提交的身份头会由全局过滤器覆盖。</p>
 */
@Component
public final class ConfiguredStubAuthenticationAdapter implements GatewayAuthenticationPort {
    private final WebClient webClient;
    private final String serviceToken;

    public ConfiguredStubAuthenticationAdapter(
            WebClient.Builder builder,
            @Value("${gateway.authentication.user-center-uri:http://localhost:8081}") String userCenterUri,
            @Value("${gateway.authentication.service-token:local-internal-token}") String serviceToken) {
        this.webClient = builder.baseUrl(userCenterUri).build();
        this.serviceToken = serviceToken;
    }

    @Override
    public Mono<GatewayAuthContext> authenticate(String token) {
        return webClient.post()
                .uri("/internal/v1/auth/sessions/introspect")
                .header("X-Internal-Service-Token", serviceToken)
                .bodyValue(new IntrospectionRequest(token))
                .retrieve()
                .bodyToMono(IntrospectionResponse.class)
                .timeout(Duration.ofSeconds(2))
                .filter(IntrospectionResponse::active)
                .filter(response -> response.userId() != null && !response.userId().isBlank())
                .map(response -> new GatewayAuthContext(response.userId(), response.roles()))
                // 基础设施故障与无效令牌必须区分，避免把用户中心故障伪装成登录过期。
                .onErrorMap(error -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "用户中心会话校验暂不可用", error));
    }

    private record IntrospectionRequest(String accessToken) { }
    private record IntrospectionResponse(boolean active, String userId, Set<String> roles) { }
}
