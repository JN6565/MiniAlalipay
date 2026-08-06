package com.minialalipay.gateway.filter;

import com.minialalipay.gateway.audit.GatewayAuditLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSRF Token 校验过滤器测试。
 *
 * <p>覆盖安全方法跳过、Bearer Token 跳过、白名单路径、Token 缺失/格式非法和 Filter 顺序。 </p>
 */
class CsrfGlobalFilterTest {

    private final GatewayAuditLogger auditLogger = new GatewayAuditLogger();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CsrfGlobalFilter filter = new CsrfGlobalFilter(objectMapper, auditLogger);

    // ---- 安全方法跳过 ----

    @Test
    @DisplayName("GET 请求跳过 CSRF 校验")
    void getRequestsBypassCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers"));
        boolean[] chainCalled = {false};

        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("HEAD 请求跳过 CSRF 校验")
    void headRequestsBypassCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.head("/api/v1/transfers"));
        boolean[] chainCalled = {false};

        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("OPTIONS 预检请求跳过 CSRF 校验")
    void optionsPreflightBypassesCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/transfers")
                        .header(HttpHeaders.ORIGIN, "http://localhost:8001")
                        .header("Access-Control-Request-Method", "POST"));
        boolean[] chainCalled = {false};

        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }

    // ---- Bearer Token 跳过 ----

    @Test
    @DisplayName("Bearer Token 认证的 POST 请求跳过 CSRF 校验")
    void bearerTokenAuthenticatedPostBypassesCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"));
        boolean[] chainCalled = {false};

        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("Bearer Token 认证的 DELETE 请求跳过 CSRF 校验")
    void bearerTokenAuthenticatedDeleteBypassesCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.delete("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token"));
        boolean[] chainCalled = {false};

        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }

    // ---- 白名单 ----

    @Test
    @DisplayName("登录路径 POST 请求在白名单中跳过 CSRF 校验")
    void loginPathBypassesCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/login"));
        boolean[] chainCalled = {false};

        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("注册路径 POST 请求在白名单中跳过 CSRF 校验")
    void registerPathBypassesCsrf() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/auth/register"));
        boolean[] chainCalled = {false};

        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }

    // ---- CSRF Token 缺失拒绝 ----

    @Test
    @DisplayName("无 Bearer Token 且无 CSRF Token 的 POST 请求返回 403")
    void postWithoutBearerOrCsrfTokenReturns403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/transfers"));
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-test-001");

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).contains("COMMON_FORBIDDEN");
        assertThat(body).contains("req-test-001");
    }

    @Test
    @DisplayName("无 Bearer Token 且 CSRF Token 过短返回 403")
    void shortCsrfTokenReturns403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/v1/p2p-collections/orders/1")
                        .header(CsrfGlobalFilter.CSRF_TOKEN_HEADER, "abc"));
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-short");

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("无 Bearer Token 且 CSRF Token 含非法字符返回 403")
    void csrfTokenWithIllegalCharactersReturns403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/transfers")
                        .header(CsrfGlobalFilter.CSRF_TOKEN_HEADER, "<script>alert(1)</script>"));
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-illegal");

        filter.filter(exchange, downstream -> Mono.empty()).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ---- Bearer Token 带 CSRF Token 仍跳过 ----

    @Test
    @DisplayName("同时有 Bearer Token 和合法 CSRF Token 时正常通过")
    void bearerTokenWithCsrfTokenPassesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/transfers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer valid-token")
                        .header(CsrfGlobalFilter.CSRF_TOKEN_HEADER, "validCsrfToken123456"));
        boolean[] chainCalled = {false};

        filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        }).block();

        assertThat(chainCalled[0]).isTrue();
    }

    // ---- Filter 顺序 ----

    @Test
    @DisplayName("Filter 顺序使用命名常量")
    void usesNamedOrderConstant() {
        assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrders.CSRF);
    }
}
