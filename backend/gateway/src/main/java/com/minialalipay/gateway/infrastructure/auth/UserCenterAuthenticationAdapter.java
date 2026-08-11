package com.minialalipay.gateway.infrastructure.auth;

import com.minialalipay.gateway.application.port.GatewayAuthenticationPort;
import com.minialalipay.gateway.infrastructure.config.GatewayAuthenticationProperties;
import com.minialalipay.gateway.application.security.GatewayAuthContext;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Set;

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
@ConditionalOnProperty(name = "gateway.authentication.stub.enabled", havingValue = "false", matchIfMissing = true)
public final class UserCenterAuthenticationAdapter implements GatewayAuthenticationPort {

    private static final Logger log = LoggerFactory.getLogger(UserCenterAuthenticationAdapter.class);

    private final WebClient webClient;
    private final String serviceToken;
    private final String userCenterUri;

    @Autowired
    public UserCenterAuthenticationAdapter(
            WebClient.Builder builder,
            ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancerFilter,
            GatewayAuthenticationProperties properties) {
        this(builder, loadBalancerFilter, properties.getUserCenterUri(), properties.getServiceToken());
    }

    public UserCenterAuthenticationAdapter(
            WebClient.Builder builder,
            ObjectProvider<ReactorLoadBalancerExchangeFilterFunction> loadBalancerFilter,
            String userCenterUri,
            String serviceToken) {
        // 默认基址为 lb://user-center，经 Nacos 负载均衡解析实例；直连 http:// 地址不添加负载均衡过滤器，
        // 否则 ReactorLoadBalancerExchangeFilterFunction 会把 http:// 的 hostname 当成 Nacos 服务名去查找，导致 503。
        WebClient.Builder configured = builder.baseUrl(userCenterUri);
        if (userCenterUri.startsWith("lb://")) {
            loadBalancerFilter.ifAvailable(filter -> {
                log.info("使用 lb:// 协议，已添加 ReactorLoadBalancerExchangeFilterFunction");
                configured.filter(filter);
            });
        } else {
            log.info("使用直连地址 {}，跳过负载均衡过滤器", userCenterUri);
        }
        this.webClient = configured.build();
        this.serviceToken = serviceToken;
        this.userCenterUri = userCenterUri;
    }

    @PostConstruct
    void logConfiguration() {
        log.info("UserCenterAuthenticationAdapter 已激活: userCenterUri={}, serviceToken={}",
                userCenterUri, serviceToken != null ? "***" : "null");
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
                .doOnNext(resp -> log.debug("用户中心会话校验响应: active={}, userId={}", resp.active(), resp.userId()))
                .filter(IntrospectionResponse::active)
                .filter(response -> response.userId() != null && !response.userId().isBlank())
                .map(response -> new GatewayAuthContext(response.userId(), response.roles()))
                // 基础设施故障与无效令牌必须区分，避免把用户中心故障伪装成登录过期。
                .doOnError(error -> log.warn("用户中心会话校验失败: userCenterUri={}, error={}",
                        userCenterUri, error.toString()))
                .onErrorMap(error -> new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "用户中心会话校验暂不可用", error));
    }

    private record IntrospectionRequest(String accessToken) { }
    private record IntrospectionResponse(boolean active, String userId, Set<String> roles) { }
}
