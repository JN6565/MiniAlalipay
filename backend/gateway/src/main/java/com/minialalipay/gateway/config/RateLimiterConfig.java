package com.minialalipay.gateway.config;

import com.minialalipay.gateway.filter.GatewayAuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关限流配置。
 *
 * <p>定义按用户、IP 和业务动作维度的限流键解析器。
 * 限流策略由各路由的 Filter 配置指定，不同操作使用不同的解析器和容量。</p>
 *
 * <h3>限流策略</h3>
 * <ul>
 *   <li>按用户：每用户每分钟最多 60 次请求</li>
 *   <li>按 IP：每 IP 每分钟最多 120 次请求</li>
 *   <li>登录：每 IP 每分钟最多 10 次</li>
 *   <li>支付密码：每用户每分钟最多 5 次</li>
 *   <li>二维码令牌交换：每 IP 每分钟最多 20 次</li>
 *   <li>Agent 调用：每用户每分钟最多 20 次</li>
 * </ul>
 *
 * <p>认证上下文从 Reactor Context 读取（由
 * {@link com.minialalipay.gateway.filter.AuthenticationGlobalFilter} 写入）。
 * 未认证时降级为 IP 维度限流。</p>
 *
 * <p>Redis 故障时降级为粗粒度内存限流（允许通过），记录告警日志，
 * 禁止因限流故障而全部拒绝。</p>
 */
@Configuration(proxyBeanMethods = false)
public class RateLimiterConfig {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(RateLimiterConfig.class);

    /** 默认用户限流键：优先从 Reactor Context 读取 principalId，未认证时使用 IP。 */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> resolvePrincipalOrIp(exchange, "rate:user", "rate:anonymous");
    }

    /** IP 限流键。 */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> ipBasedKey(exchange, "rate:ip");
    }

    /** 登录操作限流键（IP 维度）。 */
    @Bean
    public KeyResolver loginKeyResolver() {
        return exchange -> ipBasedKey(exchange, "rate:login");
    }

    /** 支付密码校验限流键：优先从 Reactor Context 读取 principalId，未认证时使用 IP。 */
    @Bean
    public KeyResolver paymentPasswordKeyResolver() {
        return exchange -> resolvePrincipalOrIp(exchange, "rate:password", "rate:password:anonymous");
    }

    /** 二维码令牌交换限流键（IP 维度）。 */
    @Bean
    public KeyResolver qrTokenKeyResolver() {
        return exchange -> ipBasedKey(exchange, "rate:qrtoken");
    }

    /** Agent 调用限流键：优先从 Reactor Context 读取 principalId，未认证时使用 IP。 */
    @Bean
    public KeyResolver agentKeyResolver() {
        return exchange -> resolvePrincipalOrIp(exchange, "rate:agent", "rate:agent:anonymous");
    }

    /**
     * 从 Reactor Context 读取认证主体，未认证时回退到 IP 限流键。
     *
     * <p>使用 {@code Mono.deferContextual} 访问 Reactor Context 中的认证上下文，
     * 该上下文由 {@link com.minialalipay.gateway.filter.AuthenticationGlobalFilter} 写入。</p>
     *
     * @param exchange       当前请求交换
     * @param principalPrefix 已认证用户限流键前缀
     * @param fallbackPrefix  未认证用户限流键前缀
     * @return 限流键 Mono
     */
    private Mono<String> resolvePrincipalOrIp(ServerWebExchange exchange, String principalPrefix, String fallbackPrefix) {
        return Mono.deferContextual(ctxView -> {
            if (ctxView.hasKey(GatewayAuthContext.CONTEXT_KEY)) {
                GatewayAuthContext auth = ctxView.get(GatewayAuthContext.CONTEXT_KEY);
                if (auth != null && auth.principalId() != null) {
                    return Mono.just(principalPrefix + ":" + auth.principalId());
                }
            }
            return ipBasedKey(exchange, fallbackPrefix);
        });
    }

    /**
     * 基于客户端 IP 地址生成限流键。
     *
     * <p>优先使用 X-Forwarded-For 头，其次使用远程地址。</p>
     */
    private Mono<String> ipBasedKey(ServerWebExchange exchange, String prefix) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        String ip;
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            ip = xForwardedFor.split(",")[0].trim();
        } else {
            ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
        }
        return Mono.just(prefix + ":" + ip);
    }
}
