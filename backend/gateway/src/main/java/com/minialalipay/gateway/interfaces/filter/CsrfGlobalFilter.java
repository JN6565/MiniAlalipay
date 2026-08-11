package com.minialalipay.gateway.interfaces.filter;

import com.minialalipay.gateway.application.security.GatewayAuthContext;
import com.minialalipay.gateway.infrastructure.audit.AuditEvent;
import com.minialalipay.gateway.infrastructure.audit.GatewayAuditLogger;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.CommonErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * CSRF（跨站请求伪造）Token 校验过滤器。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>对状态变更请求（POST/PUT/PATCH/DELETE）校验 {@code X-CSRF-Token} 头</li>
 *   <li>Bearer Token 认证的请求自动跳过（Bearer 机制天然免疫 CSRF）</li>
 *   <li>GET/HEAD/OPTIONS 请求跳过</li>
 *   <li>登录和注册等白名单路径跳过</li>
 *   <li>校验失败返回 403 {@code COMMON_FORBIDDEN} 并记录审计日志</li>
 * </ul>
 *
 * <p><b>Cookie 会话场景：</b>当客户端通过 HttpOnly Cookie 承载会话时，
 * 同源写请求必须携带服务端签发的 CSRF Token。Token 与会话绑定，
 * 由用户中心在登录时下发。本过滤器校验 Token 存在性和格式合法性，
 * 具体 Token 值的验证由下游服务完成。</p>
 *
 * <p><b>安全约束：</b></p>
 * <ul>
 *   <li>CSRF 校验不能替代服务端对象级授权</li>
 *   <li>校验失败时审计日志不包含客户端提交的 CSRF Token 原文</li>
 *   <li>H5 扫码和 C2C 令牌交换路径是 CSRF 的重点防护目标</li>
 * </ul>
 */
@Component
public final class CsrfGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CsrfGlobalFilter.class);

    /** CSRF Token 头名称。 */
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";

    /** CSRF Token 最短长度（至少 16 位十六进制字符）。 */
    private static final int MIN_TOKEN_LENGTH = 16;

    /** CSRF Token 允许的字符格式：大小写字母、数字、连字符和下划线。 */
    private static final Pattern SAFE_CSRF_TOKEN = Pattern.compile("^[A-Za-z0-9_-]+$");

    /** 不需要 CSRF 校验的精确匹配路径。 */
    private static final List<String> CSRF_WHITELIST_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login"
    );

    /** 仅包含 GET 请求的 CSRF 白名单路径前缀。 */
    private static final List<String> CSRF_WHITELIST_PREFIXES = List.of(
            "/actuator/"
    );

    /** OPTIONS 预检请求始终跳过校验。 */
    private static final String OPTIONS_METHOD = "OPTIONS";

    private final ObjectMapper objectMapper;
    private final GatewayAuditLogger auditLogger;

    public CsrfGlobalFilter(ObjectMapper objectMapper, GatewayAuditLogger auditLogger) {
        this.objectMapper = objectMapper;
        this.auditLogger = auditLogger;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String method = resolveMethod(exchange);
        String path = exchange.getRequest().getURI().getPath();

        // 安全方法和白名单不需要 CSRF 校验
        if (isSafeMethod(method) || isCsrfWhitelisted(path, method)) {
            return chain.filter(exchange);
        }

        // Bearer Token 认证天然免疫 CSRF，跳过校验
        if (hasBearerToken(exchange)) {
            return chain.filter(exchange);
        }

        // Cookie 会话写请求必须携带合法 CSRF Token
        String csrfToken = exchange.getRequest().getHeaders().getFirst(CSRF_TOKEN_HEADER);
        if (!isValidCsrfToken(csrfToken)) {
            return rejectCsrf(exchange, path, method, csrfToken);
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.CSRF;
    }

    /**
     * 判断是否为不需要 CSRF 校验的安全方法。
     *
     * <p>GET、HEAD、OPTIONS 不改变服务端状态，无需 CSRF 防护。</p>
     */
    private boolean isSafeMethod(String method) {
        return "GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || OPTIONS_METHOD.equalsIgnoreCase(method);
    }

    /**
     * 判断路径是否在 CSRF 白名单中。
     */
    private boolean isCsrfWhitelisted(String path, String method) {
        if (path == null) {
            return false;
        }
        if (CSRF_WHITELIST_PATHS.contains(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method)) {
            for (String prefix : CSRF_WHITELIST_PREFIXES) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 检查请求是否使用 Bearer Token 认证。
     *
     * <p>Bearer Token 由 JavaScript 在内存中持有并通过 Authorization 头发送，
     * 跨站请求无法读取或附加该头，因此天然免疫 CSRF。</p>
     */
    private boolean hasBearerToken(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return authHeader != null && authHeader.startsWith("Bearer ");
    }

    /**
     * 校验 CSRF Token 格式合法性。
     *
     * <p>只校验存在性和基础格式，具体值的验证（如与会话的绑定关系）
     * 由下游服务在业务边界完成。禁止在日志中记录 Token 原文。</p>
     */
    private boolean isValidCsrfToken(String token) {
        if (token == null || token.length() < MIN_TOKEN_LENGTH) {
            return false;
        }
        return SAFE_CSRF_TOKEN.matcher(token).matches();
    }

    /**
     * 构造 CSRF 拒绝响应并写入审计日志。
     *
     * <p>通过 {@code Mono.deferContextual} 延迟读取认证上下文，
     * 不阻塞事件循环线程。</p>
     */
    private Mono<Void> rejectCsrf(ServerWebExchange exchange, String path, String method, String csrfToken) {
        String requestId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID);
        String traceId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_TRACE_ID);

        String reason;
        if (csrfToken == null) {
            reason = "CSRF Token 缺失";
        } else if (csrfToken.length() < MIN_TOKEN_LENGTH) {
            reason = "CSRF Token 长度不足";
        } else {
            reason = "CSRF Token 格式非法";
        }

        String finalReason = reason;
        return Mono.deferContextual(ctxView -> {
            String principalId = resolvePrincipalFromContext(ctxView);
            log.warn("CSRF 校验拒绝: path={}, method={}, reason={}, requestId={}", path, method, finalReason, requestId);
            auditLogger.logRejection(AuditEvent.CSRF_REJECTED, requestId, traceId, principalId,
                    path, method, finalReason, "Cookie会话写请求缺少有效CSRF Token");

            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

            ApiResponse<Void> body = ApiResponse.failure(CommonErrorCode.FORBIDDEN, requestId, traceId);
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(body);
                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                return exchange.getResponse().writeWith(Mono.just(buffer));
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    private String resolveMethod(ServerWebExchange exchange) {
        HttpMethod httpMethod = exchange.getRequest().getMethod();
        return httpMethod != null ? httpMethod.name() : "GET";
    }

    /**
     * 从 Reactor Context 中读取认证主体，未认证时返回 "-"。
     *
     * <p>不调用 {@code block()}，仅在 Reactor 操作符链中由
     * {@code deferContextual} 安全调用。</p>
     */
    private String resolvePrincipalFromContext(reactor.util.context.ContextView ctxView) {
        if (ctxView.hasKey(GatewayAuthContext.CONTEXT_KEY)) {
            GatewayAuthContext auth = ctxView.get(GatewayAuthContext.CONTEXT_KEY);
            if (auth != null && auth.principalId() != null) {
                return auth.principalId();
            }
        }
        return "-";
    }
}
