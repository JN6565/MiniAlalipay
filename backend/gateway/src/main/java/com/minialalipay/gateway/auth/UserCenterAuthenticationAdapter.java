package com.minialalipay.gateway.auth;

import com.minialalipay.gateway.filter.GatewayAuthContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.time.Duration;

/**
 * 用户中心会话认证适配器。
 *
 * <p>网关通过用户中心的会话校验接口（{@code POST /internal/v1/auth/sessions/introspect}）
 * 验证 Bearer 令牌的真实性，并将返回的用户标识和角色写入认证上下文。</p>
 *
 * <p>通过调用用户中心 {@code /internal/v1/auth/sessions/introspect} 校验会话令牌。
 * 网关只信任用户中心返回的用户 ID 和角色，客户端提交的身份头会由全局过滤器覆盖。</p>
 *
 * <h3>故障处理</h3>
 * <ul>
 *   <li>用户中心不可达时抛出 {@code SERVICE_UNAVAILABLE}，与令牌无效的 401 区分开</li>
 *   <li>超时 2 秒，避免阻塞网关请求链</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "gateway.authentication.stub-enabled", havingValue = "false", matchIfMissing = true)
public final class UserCenterAuthenticationAdapter implements GatewayAuthenticationPort {
    private final WebClient webClient;
    private final String serviceToken;

    public UserCenterAuthenticationAdapter(
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
