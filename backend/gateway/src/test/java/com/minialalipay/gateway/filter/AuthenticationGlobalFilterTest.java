package com.minialalipay.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身份认证过滤器测试。
 */
class AuthenticationGlobalFilterTest {

    private AuthenticationGlobalFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new AuthenticationGlobalFilter(objectMapper);
    }

    @Test
    @DisplayName("白名单路径 /actuator/health 跳过认证")
    void actuatorHealthBypassesAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
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
        MockServerWebExchange exchange = MockServerWebExchange.from(
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
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login"));

        boolean[] chainCalled = {false};
        Mono<Void> result = filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        });

        StepVerifier.create(result).verifyComplete();
        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("登录路径的相似前缀不能绕过认证")
    void loginLookalikePathRequiresAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login-anything"));

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("注册路径的下级路径不能绕过认证")
    void registerSubPathRequiresAuthentication() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/register/anything"));

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("缺少 Authorization 头返回 401")
    void missingAuthorizationHeaderReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers"));

        Mono<Void> result = filter.filter(exchange, downstream -> Mono.empty());

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("非 Bearer 格式的 Authorization 头返回 401")
    void nonBearerAuthorizationReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"));

        Mono<Void> result = filter.filter(exchange, downstream -> Mono.empty());

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("空的 Bearer 令牌返回 401")
    void emptyBearerTokenReturns401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "));

        Mono<Void> result = filter.filter(exchange, downstream -> Mono.empty());

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("阶段二 Stub：有效格式令牌认证通过并写入上下文")
    void validTokenPassesAuthenticationAndWritesContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-dev-token-12345"));

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

    @Test
    @DisplayName("短令牌（少于8字符）被拒绝")
    void shortTokenIsRejected() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer abc"));

        Mono<Void> result = filter.filter(exchange, downstream -> Mono.empty());

        StepVerifier.create(result).verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Filter 顺序使用命名常量")
    void usesNamedOrderConstant() {
        assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrders.AUTHENTICATION);
    }

    @Test
    @DisplayName("POST 到白名单 auth 路径跳过认证")
    void postToAuthWhitelistPassesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login"));

        boolean[] chainCalled = {false};
        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // 响应未被设置
    }
}
