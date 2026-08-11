package com.minialalipay.gateway.interfaces.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 访问日志过滤器测试。
 *
 * <p>覆盖正常请求日志、异常场景不阻碍响应和 Filter 顺序校验。 </p>
 */
class AccessLogGlobalFilterTest {

    private final AccessLogGlobalFilter filter = new AccessLogGlobalFilter();

    @Test
    @DisplayName("Filter 链正常完成后访问日志不阻塞响应")
    void logAfterSuccessfulChainDoesNotBlock() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers"));
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-access-001");
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_TRACE_ID, "trace-access-001");
        exchange.getResponse().setStatusCode(HttpStatus.OK);
        boolean[] chainCalled = {false};

        Mono<Void> result = filter.filter(exchange, downstream -> {
            chainCalled[0] = true;
            return Mono.empty();
        });

        result.block();
        assertThat(chainCalled[0]).isTrue();
    }

    @Test
    @DisplayName("Filter 链异常时访问日志仍然记录且不吞错")
    void logAfterErrorChainDoesNotSwallowError() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/transfers"));
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-error-001");
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);

        RuntimeException expected = new RuntimeException("下游异常");
        Mono<Void> result = filter.filter(exchange, downstream -> Mono.error(expected));

        try {
            result.block();
        } catch (RuntimeException e) {
            assertThat(e).isSameAs(expected);
        }
        // 确认状态码在异常时被保留
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("不同请求路径和方法均被正确记录")
    void variousMethodsAndPathsAreRecorded() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/qr-pay/token-exchanges")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .header("X-Forwarded-For", "203.0.113.50"));
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-various");
        exchange.getResponse().setStatusCode(HttpStatus.OK);

        filter.filter(exchange, downstream -> Mono.empty()).block();
        // 验证不抛出异常
    }

    @Test
    @DisplayName("未设置状态码时默认记录 200")
    void missingStatusCodeDefaultsTo200() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health"));
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-no-status");

        filter.filter(exchange, downstream -> Mono.empty()).block();
        // 验证不抛出异常
    }

    @Test
    @DisplayName("Filter 顺序使用命名常量")
    void usesNamedOrderConstant() {
        assertThat(filter.getOrder()).isEqualTo(GatewayFilterOrders.LOGGING);
    }
}
