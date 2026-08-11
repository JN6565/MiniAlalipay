package com.minialalipay.gateway.infrastructure.auth;

import com.minialalipay.gateway.application.port.GatewayAuthenticationPort;
import com.minialalipay.gateway.application.security.GatewayAuthContext;
import com.minialalipay.gateway.infrastructure.config.GatewayAuthCacheConfiguration;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关会话校验结果的进程内短 TTL 缓存装饰。
 *
 * <p>包装实际认证端口（用户中心回源适配器或本地演示桩），对同一会话令牌的校验结果
 * 缓存 {@link #TTL_SECONDS} 秒，避免支付等连续请求每次都远程调用用户中心
 * {@code /internal/v1/auth/sessions/introspect}，显著降低每个请求的鉴权往返耗时。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>缓存键为会话令牌的 SHA-256 摘要，进程内存中不保留令牌原文</li>
 *   <li>只缓存校验通过的结果；无效令牌不缓存，401 语义保持实时</li>
 *   <li>TTL 上限约束登出感知延迟：登出后最长 {@link #TTL_SECONDS} 秒内网关仍可能放行旧会话</li>
 *   <li>容量上限防止恶意大量不同令牌导致内存膨胀</li>
 * </ul>
 *
 * <p>由 {@link GatewayAuthCacheConfiguration} 按当前激活的认证适配器装配为 @Primary Bean，
 * 全局过滤器注入到的即是带缓存版本。</p>
 */
public final class CachedGatewayAuthenticationPort implements GatewayAuthenticationPort {

    /** 校验结果缓存有效期（秒）；即登出在网关侧的最大感知延迟。 */
    private static final long TTL_SECONDS = 30;

    /** 缓存条目上限，超限清空重建；单网关实例进程内缓存。 */
    private static final int MAX_ENTRIES = 1000;

    private final GatewayAuthenticationPort delegate;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public CachedGatewayAuthenticationPort(GatewayAuthenticationPort delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<GatewayAuthContext> authenticate(String token) {
        String key = digest(token);
        CacheEntry entry = cache.get(key);
        Instant now = Instant.now();
        if (entry != null && entry.expiresAt().isAfter(now)) {
            return Mono.just(entry.context());
        }
        if (entry != null) {
            cache.remove(key);
        }
        return delegate.authenticate(token)
                .doOnNext(context -> {
                    // 只缓存通过校验的结果；无效令牌保持实时 401，不进缓存
                    if (cache.size() >= MAX_ENTRIES) {
                        cache.clear();
                    }
                    cache.put(key, new CacheEntry(context, now.plusSeconds(TTL_SECONDS)));
                });
    }

    /** 计算会话令牌的 SHA-256 摘要作为缓存键，避免内存中持有令牌原文。 */
    private static String digest(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", e);
        }
    }

    /** 缓存条目：认证上下文与过期时间。 */
    private record CacheEntry(GatewayAuthContext context, Instant expiresAt) { }
}
