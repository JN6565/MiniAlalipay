package com.minialalipay.gateway.interfaces.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.gateway.interfaces.filter.RequestIdGlobalFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关全局异常处理器测试。
 *
 * <p>覆盖全部下游错误码映射和网关自身异常处理。</p>
 */
class GatewayExceptionHandlerTest {

    private GatewayExceptionHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new GatewayExceptionHandler(objectMapper);
    }

    @Test
    @DisplayName("下游 401 映射为 COMMON_UNAUTHORIZED")
    void downstream401MapsToUnauthorized() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(getResponseBody(exchange)).contains("COMMON_UNAUTHORIZED");
    }

    @Test
    @DisplayName("下游 403 映射为 COMMON_FORBIDDEN")
    void downstream403MapsToForbidden() {
        MockServerWebExchange exchange = buildExchange("/api/v1/admin/users");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN);

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(getResponseBody(exchange)).contains("COMMON_FORBIDDEN");
    }

    @Test
    @DisplayName("下游 404 保留下游错误码")
    void downstream404PreservesDownstreamErrorCode() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers/unknown");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getResponseBody(exchange)).contains("ORDER_NOT_FOUND");
    }

    @Test
    @DisplayName("下游 409 保留冲突错误码")
    void downstream409PreservesConflictCode() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT, "VERSION_CONFLICT");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(getResponseBody(exchange)).contains("VERSION_CONFLICT");
    }

    @Test
    @DisplayName("下游 422 保留请求无法处理语义")
    void downstream422PreservesUnprocessableSemantics() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        ResponseStatusException ex = new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(getResponseBody(exchange)).contains("BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("下游 429 映射为 RATE_LIMITED")
    void downstream429MapsToRateLimited() {
        MockServerWebExchange exchange = buildExchange("/api/v1/agent/messages");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(getResponseBody(exchange)).contains("RATE_LIMITED");
        assertThat(getResponseBody(exchange)).contains("请求过于频繁");
    }

    @Test
    @DisplayName("下游 503 映射为服务不可用")
    void downstream503MapsToServiceUnavailable() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(getResponseBody(exchange)).contains("服务暂时不可用");
    }

    @Test
    @DisplayName("下游 500 映射为内部错误，不泄露内部信息")
    void downstream500MapsToInternalErrorWithoutLeakingInternals() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "java.sql.SQLException: connection refused at db.internal:3306");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String body = getResponseBody(exchange);
        assertThat(body).contains("COMMON_INTERNAL_ERROR");
        assertThat(body).doesNotContain("java.sql");
        assertThat(body).doesNotContain("3306");
    }

    @Test
    @DisplayName("连接异常映射为 503")
    void connectExceptionMapsTo503() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        ConnectException ex = new ConnectException("Connection refused");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        String body = getResponseBody(exchange);
        assertThat(body).contains("服务暂时不可用");
    }

    @Test
    @DisplayName("超时异常映射为 504")
    void timeoutExceptionMapsTo504() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        TimeoutException ex = new TimeoutException("Read timed out");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        String body = getResponseBody(exchange);
        assertThat(body).contains("请求处理超时");
    }

    @Test
    @DisplayName("参数非法异常映射为 400")
    void illegalArgumentExceptionMapsTo400() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        IllegalArgumentException ex = new IllegalArgumentException("amountFen must be positive");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String body = getResponseBody(exchange);
        assertThat(body).contains("COMMON_INVALID_REQUEST");
    }

    @Test
    @DisplayName("未预期异常映射为 500，使用中文错误信息")
    void unexpectedExceptionMapsTo500WithChineseMessage() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        RuntimeException ex = new RuntimeException("Something went wrong");

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String body = getResponseBody(exchange);
        assertThat(body).contains("系统内部错误");
    }

    @Test
    @DisplayName("所有错误响应均包含 requestId")
    void allErrorResponsesContainRequestId() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-test-001");
        RuntimeException ex = new RuntimeException("test");

        handler.handle(exchange, ex).block();

        String body = getResponseBody(exchange);
        assertThat(body).contains("req-test-001");
    }

    @Test
    @DisplayName("错误响应使用中文，不包含英文兜底消息")
    void errorResponseUsesChineseNotEnglish() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers")
                        .header(RequestIdGlobalFilter.HEADER_NAME, "req-chinese-test"));
        RuntimeException ex = new RuntimeException("test");

        handler.handle(exchange, ex).block();

        String body = getResponseBody(exchange);
        assertThat(body).contains("系统内部错误");
        assertThat(body).doesNotContain("Internal server error");
        assertThat(body).contains("req-chinese-test");
    }

    @Test
    @DisplayName("下游 404 无 reason 时使用默认 NOT_FOUND 错误码")
    void downstream404WithoutReasonUsesDefaultNotFoundCode() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers/unknown");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND);

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getResponseBody(exchange)).contains("COMMON_NOT_FOUND");
    }

    @Test
    @DisplayName("下游 409 无 reason 时使用默认冲突错误码")
    void downstream409WithoutReasonUsesDefaultConflictCode() {
        MockServerWebExchange exchange = buildExchange("/api/v1/transfers");
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.CONFLICT);

        handler.handle(exchange, ex).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(getResponseBody(exchange)).contains("COMMON_CONFLICT");
    }

    private MockServerWebExchange buildExchange(String path) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get(path)
                        .header(RequestIdGlobalFilter.HEADER_NAME, "req-test-001"));
    }

    private String getResponseBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString()
                .defaultIfEmpty("")
                .block();
    }
}
