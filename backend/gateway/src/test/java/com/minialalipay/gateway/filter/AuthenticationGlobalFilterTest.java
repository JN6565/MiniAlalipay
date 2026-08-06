package com.minialalipay.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.gateway.audit.GatewayAuditLogger;
import com.minialalipay.gateway.auth.GatewayAuthenticationPort;
import com.minialalipay.gateway.auth.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份认证过滤器测试。
 *
 * <p>覆盖白名单、令牌校验、伪造身份头清洗、运维角色门禁和 OPTIONS 透传。 </p>
 */
class AuthenticationGlobalFilterTest {

    /** 合法 Stub 令牌对应的认证端口。 */
    private static final GatewayAuthenticationPort PASSING_PORT =
            token -> "valid-dev-token".equals(token)
                    ? Mono.just(new GatewayAuthContext("dev-user-001", Set.of("USER")))
                    : Mono.empty();

    private static final GatewayAuthenticationPort OPERATOR_PORT =
            token -> "operator-token".equals(token)
                    ? Mono.just(new GatewayAuthContext("ops-001", Set.of("OPERATOR")))
                    : Mono.empty();

    private static final GatewayAuthenticationPort ADMIN_PORT =
            token -> "admin-token".equals(token)
                    ? Mono.just(new GatewayAuthContext("adm-001", Set.of("ADMIN")))
                    : Mono.empty();

    /** 永远拒绝的端口（模拟未配置 Stub 的生产环境）。 */
    private static final GatewayAuthenticationPort REJECTING_PORT =
            token -> Mono.empty();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GatewayAuditLogger auditLogger = new GatewayAuditLogger();
    private final JwtService jwtService = new JwtService("test-secret-for-filter-tests-0123456789");

    /** 创建过滤器实例的便捷方法。 */
    private AuthenticationGlobalFilter createFilter(GatewayAuthenticationPort port) {
        return new AuthenticationGlobalFilter(objectMapper, port, auditLogger, jwtService);
    }

    // ---- 白名单测试 ----

    @Test
    @DisplayName("白名单路径 /actuator/health 跳过认证")
    void actuatorHealthBypassesAuthentication() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));

        boolean[] chainCalled = {false};
        Mono<Void> result = filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        });

        StepVerifier.create(result).verifyComplete();
        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("白名单路径 /api/v1/auth/register 跳过认证")
    void registerPathBypassesAuthentication() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/register"));

        boolean[] chainCalled = {false};
        Mono<Void> result = filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        });

        StepVerifier.create(result).verifyComplete();
        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("白名单路径 /api/v1/auth/login 跳过认证")
    void loginPathBypassesAuthentication() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login"));

        boolean[] chainCalled = {false};
        Mono<Void> result = filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        });

        StepVerifier.create(result).verifyComplete();
        assertThat(chainCalled[0]).isTrue();
    }

    // ---- 路径变体拒绝测试 ----

    @Test
    @DisplayName("登录路径的相似前缀不能绕过认证")
    void loginLookalikePathRequiresAuthentication() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login-anything"));

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("注册路径的下级路径不能绕过认证")
    void registerSubPathRequiresAuthentication() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/register/anything"));

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- 令牌缺失/格式测试 ----

    @Test
    @DisplayName("缺少 Authorization 头返回 401")
    void missingAuthorizationHeaderReturns401() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers"));

        Mono<Void> result = filter.filter(exchange, downstream -> Mono.empty());

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("非 Bearer 格式的 Authorization 头返回 401")
    void nonBearerAuthorizationReturns401() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"));

        Mono<Void> result = filter.filter(exchange, downstream -> Mono.empty());

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("空的 Bearer 令牌返回 401")
    void emptyBearerTokenReturns401() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "));

        Mono<Void> result = filter.filter(exchange, downstream -> Mono.empty());

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("未配置令牌时全部拒绝")
    void rejectingPortRefusesAnyToken() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer some-unknown-token"));

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- 合法令牌测试 ----

    @Test
    @DisplayName("精确匹配的令牌认证通过并写入上下文")
    void validTokenPassesAuthenticationAndWritesContext() {
        var filter = createFilter(PASSING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-dev-token"));

        GatewayAuthContext[] capturedContext = new GatewayAuthContext[1];
        boolean[] chainCalled = {false};

        Mono<Void> result = filter.filter(exchange, downstream ->
                Mono.deferContextual(ctxView -> {
                    chainCalled[0] = true;
                    if (ctxView.hasKey(GatewayAuthContext.CONTEXT_KEY)) {
                        capturedContext[0] = ctxView.get(GatewayAuthContext.CONTEXT_KEY);
                    }
                    return Mono.empty();
                })
        );

        StepVerifier.create(result).verifyComplete();
        assertThat(chainCalled[0]).isTrue();
        assertThat(capturedContext[0]).isNotNull();
        assertThat(capturedContext[0].principalId()).isEqualTo("dev-user-001");
        assertThat(capturedContext[0].roles()).contains("USER");
    }

    // ---- 身份头清洗测试 ----

    @Test
    @DisplayName("认证主体覆盖客户端伪造的用户身份头")
    void authenticatedPrincipalOverridesForgedUserHeader() {
        var filter = createFilter(PASSING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/accounts/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-dev-token")
                        .header(AuthenticationGlobalFilter.USER_ID_HEADER, "forged-user"));

        AtomicReference<List<String>> downstreamUserIds = new AtomicReference<>();
        Mono<Void> result = filter.filter(exchange, downstream -> {
            downstreamUserIds.set(downstream.getRequest().getHeaders()
                    .get(AuthenticationGlobalFilter.USER_ID_HEADER));
            return Mono.empty();
        });

        StepVerifier.create(result).verifyComplete();
        assertThat(downstreamUserIds.get()).containsExactly("dev-user-001");
    }

    @Test
    @DisplayName("白名单路径也清洗伪造身份头")
    void whitelistPathAlsoStripsForgedIdentityHeaders() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login")
                        .header(AuthenticationGlobalFilter.USER_ID_HEADER, "forged-user")
                        .header(AuthenticationGlobalFilter.ROLES_HEADER, "ADMIN"));

        AtomicReference<List<String>> downstreamRoles = new AtomicReference<>();
        filter.filter(exchange, downstream -> {
            downstreamRoles.set(downstream.getRequest().getHeaders()
                    .get(AuthenticationGlobalFilter.ROLES_HEADER));
            return Mono.empty();
        }).block();

        assertThat(downstreamRoles.get()).isNull();
    }

    // ---- 运维角色门禁 ----

    @Test
    @DisplayName("普通用户访问运维路径返回 403")
    void normalUserAccessingOpsPathReturns403() {
        var filter = createFilter(PASSING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/ops/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-dev-token"));

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("运维角色可以访问通用运维路径")
    void operatorCanAccessOpsPath() {
        var filter = createFilter(OPERATOR_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/ops/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-token"));

        boolean[] chainCalled = {false};
        Mono<Void> result = filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        });

        StepVerifier.create(result).verifyComplete();
        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("运营人员访问信用运维路径返回 403")
    void operatorCannotAccessCreditOps() {
        var filter = createFilter(OPERATOR_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/ops/credit/statement-runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer operator-token"));

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("系统管理员可以触发信用运维任务")
    void adminCanAccessCreditOps() {
        var filter = createFilter(ADMIN_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/ops/credit/due-check-runs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"));

        boolean[] chainCalled = {false};
        Mono<Void> result = filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        });

        StepVerifier.create(result).verifyComplete();
        assertThat(chainCalled[0]).isTrue();
    }

    // ---- OPTIONS 预检 ----

    @Test
    @DisplayName("OPTIONS 预检请求跳过认证")
    void optionsPreflightBypassesAuthentication() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/transfers")
                        .header(HttpHeaders.ORIGIN, "http://localhost:8001")
                        .header("Access-Control-Request-Method", "POST"));

        boolean[] chainCalled = {false};
        Mono<Void> result = filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        });

        StepVerifier.create(result).verifyComplete();
        assertThat(chainCalled[0]).isTrue();
    }

    // ---- Filter 顺序 ----

    @Test
    @DisplayName("Filter 顺序使用命名常量")
    void usesNamedOrderConstant() {
        var filter = createFilter(REJECTING_PORT);
        assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrders.AUTHENTICATION);
    }

    @Test
    @DisplayName("POST 到白名单 auth 路径跳过认证")
    void postToAuthWhitelistPassesThrough() {
        var filter = createFilter(REJECTING_PORT);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login"));

        boolean[] chainCalled = {false};
        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
