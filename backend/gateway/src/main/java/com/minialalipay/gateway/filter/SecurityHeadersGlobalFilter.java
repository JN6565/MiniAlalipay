package com.minialalipay.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 安全响应头过滤器。
 *
 * <p>为所有响应添加标准安全头，并对二维码相关路径设置更严格的缓存和引用策略。</p>
 *
 * <h3>全局安全头</h3>
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — 禁止 MIME 类型嗅探</li>
 *   <li>{@code X-Frame-Options: DENY} — 禁止页面被嵌入 frame</li>
 *   <li>{@code X-XSS-Protection: 0} — 禁用浏览器旧版 XSS 过滤器</li>
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin} — 跨域时仅发送源</li>
 * </ul>
 *
 * <h3>二维码路径额外安全头</h3>
 * <p>以下路径涉及资金敏感操作，额外设置：</p>
 * <ul>
 *   <li>{@code Cache-Control: no-store} — 禁止缓存</li>
 *   <li>{@code Referrer-Policy: no-referrer} — 不发送 Referrer</li>
 *   <li>{@code X-Robots-Tag: noindex} — 禁止搜索引擎索引</li>
 * </ul>
 */
@Component
public final class SecurityHeadersGlobalFilter implements GlobalFilter, Ordered {

    /** 需要严格缓存和引用策略的二维码相关路径前缀。 */
    private static final List<String> QR_SENSITIVE_PATHS = List.of(
            "/api/v1/qr-pay/",
            "/api/v1/p2p-collections/"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getResponse().getHeaders();

        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("X-XSS-Protection", "0");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");

        String path = exchange.getRequest().getURI().getPath();
        if (isQrSensitivePath(path)) {
            headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
            headers.set("Referrer-Policy", "no-referrer");
            headers.set("X-Robots-Tag", "noindex");
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.SECURITY_HEADERS;
    }

    /**
     * 判断路径是否属于需要严格安全策略的二维码敏感路径。
     *
     * @param path 请求路径
     * @return true 表示需要额外的缓存和引用保护
     */
    private boolean isQrSensitivePath(String path) {
        if (path == null) {
            return false;
        }
        for (String prefix : QR_SENSITIVE_PATHS) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
