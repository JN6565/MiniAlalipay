package com.minialalipay.gateway.controller;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.gateway.filter.RequestIdGlobalFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关健康检查接口测试。
 */
class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    @DisplayName("健康检查返回 UP 状态")
    void healthCheckReturnsUpStatus() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/healthcheck"));
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-health-001");

        ApiResponse<Map<String, String>> response = controller.healthCheck(exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data()).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("健康检查响应包含请求编号")
    void healthCheckContainsRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/healthcheck"));
        exchange.getAttributes().put(RequestIdGlobalFilter.ATTR_REQUEST_ID, "req-health-002");

        ApiResponse<Map<String, String>> response = controller.healthCheck(exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.requestId()).isEqualTo("req-health-002");
    }

    @Test
    @DisplayName("未设置请求编号时健康检查仍可正常返回")
    void healthCheckWorksWithoutRequestId() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/healthcheck"));

        ApiResponse<Map<String, String>> response = controller.healthCheck(exchange).block();

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo("OK");
    }
}
