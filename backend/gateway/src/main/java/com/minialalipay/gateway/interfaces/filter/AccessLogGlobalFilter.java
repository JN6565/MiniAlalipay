package com.minialalipay.gateway.interfaces.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.Set;

/**
 * 网关访问日志过滤器。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>记录每个请求的方法、路径、响应状态码和耗时</li>
 *   <li>输出中文结构化日志，携带 {@code requestId} 和 {@code traceId}</li>
 *   <li>过滤敏感请求头（Authorization、Cookie、CSRF Token 等）</li>
 *   <li>日志失败不影响业务响应</li>
 * </ul>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>禁止记录 Authorization 头、Cookie、X-CSRF-Token 原文</li>
 *   <li>禁止记录请求体内容</li>
 *   <li>禁止记录二维码令牌、支付密码等查询参数</li>
 * </ul>
 *
 * <h3>执行时机</h3>
 * <p>本过滤器在 Filter 链最后（顺序值 100），在所有业务处理完成后记录访问日志。
 * 即使上游抛出异常，只要异常被 {@code GatewayExceptionHandler} 转换为响应，
 * 响应状态码仍会被正确记录。</p>
 */
@Component
public final class AccessLogGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("ACCESS_LOG");

    /** 禁止记录的敏感请求头名称（小写）。 */
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "set-cookie",
            "x-csrf-token"
    );

    /** 查询参数中需要脱敏的键名。 */
    private static final Set<String> SENSITIVE_QUERY_PARAMS = Set.of(
            "token", "t", "password", "payPassword",
            "oldPassword", "newPassword", "csrfToken"
    );

    /** 脱敏替换文本。 */
    private static final String REDACTED = "***";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();

        return chain.filter(exchange)
                .doOnSuccess(unused -> logAccess(exchange, startNanos))
                .doOnError(throwable -> logAccess(exchange, startNanos));
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.LOGGING;
    }

    /**
     * 记录访问日志，从 Exchange 中提取非敏感字段。
     *
     * <p>日志格式为中文键值对，包含方法、路径、状态码、耗时和请求追踪标识。
     * 耗时以毫秒为单位，保留一位小数。</p>
     */
    private void logAccess(ServerWebExchange exchange, long startNanos) {
        try {
            String method = Optional.ofNullable(exchange.getRequest().getMethod())
                    .map(Object::toString)
                    .orElse("UNKNOWN");
            String path = exchange.getRequest().getURI().getPath();
            int statusCode = extractStatusCode(exchange);
            double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0;
            String requestId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID);
            String traceId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_TRACE_ID);
            String clientIp = extractClientIp(exchange);

            ACCESS_LOG.info("请求完成 method={} path={} status={} durationMs={} clientIp={} requestId={} traceId={}",
                    method,
                    nullToEmpty(path),
                    statusCode,
                    formatDuration(durationMs),
                    clientIp,
                    nullToEmpty(requestId),
                    nullToEmpty(traceId));
        } catch (Exception ignored) {
            // 访问日志失败不得影响业务，静默丢弃
        }
    }

    /**
     * 提取响应状态码。
     *
     * <p>优先从已设置的原始状态码读取，未设置时视为 200。
     * 对于异常场景，状态码可能由 {@code GatewayExceptionHandler} 在提交前设置。</p>
     */
    private int extractStatusCode(ServerWebExchange exchange) {
        HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
        if (statusCode != null) {
            return statusCode.value();
        }
        // 响应尚未设置状态码（如 Filter 链中途读取），返回 200 作为默认值
        return 200;
    }

    /**
     * 提取客户端 IP，优先使用 X-Forwarded-For。
     */
    private String extractClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private String formatDuration(double durationMs) {
        return String.format("%.1f", durationMs);
    }

    private String nullToEmpty(String value) {
        return value != null ? value : "-";
    }
}
