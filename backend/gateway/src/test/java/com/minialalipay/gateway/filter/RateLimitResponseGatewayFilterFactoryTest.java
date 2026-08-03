package com.minialalipay.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限流统一响应过滤器测试。
 */
class RateLimitResponseGatewayFilterFactoryTest {

    @Test
    @DisplayName("空 429 响应转换为包含请求编号和链路编号的统一响应")
    void emptyRateLimitResponseBecomesStandardResponse() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
                        .header("X-Request-Id", "request-rate-limit-unit")
        );
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_TRACE_ID, "trace-rate-limit-unit");
        RateLimitResponseGatewayFilterFactory factory =
                new RateLimitResponseGatewayFilterFactory(new ObjectMapper());

        factory.apply(new RateLimitResponseGatewayFilterFactory.Config())
                .filter(exchange, current -> {
                    current.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    return current.getResponse().setComplete();
                })
                .block();

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(body)
                .contains("RATE_LIMITED")
                .contains("请求过于频繁")
                .contains("request-rate-limit-unit")
                .contains("trace-rate-limit-unit");
    }
}
