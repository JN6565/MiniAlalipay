package com.minialalipay.gateway.filter;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.CommonErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.gateway.auth.GatewayAuthenticationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * 身份认证全局过滤器。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>从 {@code Authorization: Bearer <token>} 提取会话令牌</li>
 *   <li>白名单路径（注册、登录、健康检查）跳过认证</li>
 *   <li>校验会话并注入 {@code principalId} 和 {@code roles} 到 Reactor Context</li>
 *   <li>未认证请求返回 401 {@code COMMON_UNAUTHORIZED}</li>
 * </ul>
 *
 * <p><b>阶段二 Stub 实现：</b>当前接受任意格式合法的 Bearer 令牌，
 * 映射为测试用户。待用户中心会话校验接口就绪后，切换为真实调用。
 * 本 Stub 不会绕过后续各服务自身的鉴权——下游仍必须校验对象权限。</p>
 *
 * <p>安全约束：</p>
 * <ul>
 *   <li>网关鉴权不能成为服务端省略鉴权的理由</li>
 *   <li>不把客户端提交的 {@code userId} 或 {@code role} 当作鉴权结果</li>
 *   <li>Cookie 登录场景需要额外 CSRF 校验</li>
 * </ul>
 */
@Component
public final class AuthenticationGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationGlobalFilter.class);

    /** 不需要认证且必须精确匹配的路径。 */
    private static final List<String> EXACT_WHITELIST_PATHS = List.of(
            "/api/v1/auth/register",
            "/api/v1/auth/login"
    );

    /** 仅包含 GET 请求的白名单路径前缀（健康检查等）。 */
    private static final List<String> GET_WHITELIST_PATHS = List.of(
            "/actuator/"
    );

    private static final String BEARER_PREFIX = "Bearer ";

    /** 网关写给下游服务的可信用户身份头，任何客户端同名值都会被覆盖。 */
    public static final String USER_ID_HEADER = "X-User-Id";

    /** 网关写给下游服务的可信角色头。 */
    public static final String ROLES_HEADER = "X-User-Roles";

    private final ObjectMapper objectMapper;
    private final GatewayAuthenticationPort authenticationPort;

    public AuthenticationGlobalFilter(
            ObjectMapper objectMapper,
            GatewayAuthenticationPort authenticationPort) {
        this.objectMapper = objectMapper;
        this.authenticationPort = authenticationPort;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name()
                : "GET";

        if ("OPTIONS".equalsIgnoreCase(method) || isWhitelisted(path, method)) {
            return chain.filter(removeUntrustedIdentityHeaders(exchange));
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return writeUnauthorizedResponse(exchange, "缺少有效的认证令牌");
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            return writeUnauthorizedResponse(exchange, "认证令牌不能为空");
        }

        return authenticationPort.authenticate(token)
                .switchIfEmpty(Mono.defer(() -> writeUnauthorizedResponse(exchange, "会话无效或已过期")
                        .then(Mono.empty())))
                .flatMap(authContext -> authorizeAndContinue(exchange, chain, path, authContext));
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.AUTHENTICATION;
    }

    private Mono<Void> authorizeAndContinue(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            String path,
            GatewayAuthContext authContext) {
        if (path.startsWith("/api/v1/ops/") && authContext.roles().stream()
                .noneMatch(role -> "ADMIN".equals(role) || "OPERATOR".equals(role) || "OBSERVER".equals(role))) {
            return writeForbiddenResponse(exchange);
        }

        log.debug("认证成功: principalId={}, roles={}, path={}",
                authContext.principalId(), authContext.roles(), path);
        ServerWebExchange authenticatedExchange = exchange.mutate()
                .request(request -> request.headers(headers -> {
                    // 保留前端传递的 X-User-Id，如果没有则使用认证上下文的 principalId
                    String existingUserId = headers.getFirst(USER_ID_HEADER);
                    if (existingUserId == null || existingUserId.isBlank()) {
                        headers.set(USER_ID_HEADER, authContext.principalId());
                    }
                    headers.set(ROLES_HEADER, String.join(",", authContext.roles()));
                }))
                .build();
        return chain.filter(authenticatedExchange)
                .contextWrite(ctx -> ctx.put(GatewayAuthContext.CONTEXT_KEY, authContext));
    }

    private ServerWebExchange removeUntrustedIdentityHeaders(ServerWebExchange exchange) {
        return exchange.mutate()
                .request(request -> request.headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(ROLES_HEADER);
                }))
                .build();
    }

    private Mono<Void> writeForbiddenResponse(ServerWebExchange exchange) {
        String requestId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID);
        String traceId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_TRACE_ID);
        ApiResponse<Void> body = ApiResponse.failure(CommonErrorCode.FORBIDDEN, requestId, traceId);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return writeBody(exchange, body);
    }

    /**
     * 判断请求路径是否在白名单中。
     */
    private boolean isWhitelisted(String path, String method) {
        if (path == null) {
            return false;
        }
        if (EXACT_WHITELIST_PATHS.contains(path)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method)) {
            for (String getPath : GET_WHITELIST_PATHS) {
                if (path.startsWith(getPath)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 写入 401 未认证响应。
     */
    private Mono<Void> writeUnauthorizedResponse(ServerWebExchange exchange, String detail) {
        String requestId = Optional.ofNullable(
                exchange.getRequest().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME))
                .orElse(exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID));
        log.warn("认证拒绝: path={}, detail={}, requestId={}",
                exchange.getRequest().getURI().getPath(), detail, requestId);

        String traceId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_TRACE_ID);
        ApiResponse<Void> body = ApiResponse.failure(CommonErrorCode.UNAUTHORIZED, requestId, traceId);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return writeBody(exchange, body);
    }

    private Mono<Void> writeBody(ServerWebExchange exchange, ApiResponse<Void> body) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
