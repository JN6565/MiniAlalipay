package com.minialalipay.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 服务 Spring Boot 真实启动集成测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AiServiceFailingTestController.class)
class AiServiceApplicationIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("应用上下文启动成功，Actuator 健康检查返回 UP")
    void applicationContextStartsAndHealthCheckReturnsUp() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("自定义 HealthController 返回统一响应格式")
    void customHealthCheckReturnsUnifiedResponse() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/healthcheck", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("code", "OK");
        assertThat(response.getBody()).containsKey("requestId");
        Map<String, String> data = (Map<String, String>) response.getBody().get("data");
        assertThat(data).containsEntry("service", "ai-service");
        assertThat(data).containsEntry("status", "UP");
    }

    @Test
    @DisplayName("所有响应均包含 X-Request-Id 头")
    void allResponsesContainXRequestIdHeader() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/actuator/healthcheck", Map.class);

        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertThat(requestId).isNotBlank();
        assertThat(response.getBody()).containsEntry("requestId", requestId);
    }

    @Test
    @DisplayName("未预期异常返回统一中文响应并保留请求编号")
    void unexpectedErrorReturnsStandardResponse() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Request-Id", "request-ai-500");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/test/ai-error",
                org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(headers),
                Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("request-ai-500");
        assertThat(response.getBody())
                .containsEntry("code", "COMMON_INTERNAL_ERROR")
                .containsEntry("message", "系统内部错误")
                .containsEntry("requestId", "request-ai-500");
    }

    @Test
    @DisplayName("未携带请求编号的异常响应使用服务端生成的请求编号")
    void unexpectedErrorUsesGeneratedRequestId() {
        ResponseEntity<Map> response = restTemplate.getForEntity("/test/ai-error", Map.class);

        String requestId = response.getHeaders().getFirst("X-Request-Id");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(requestId).isNotBlank();
        assertThat(response.getBody()).containsEntry("requestId", requestId);
    }
}
