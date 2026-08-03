package com.minialalipay.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 服务 Spring Boot 真实启动集成测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

        assertThat(response.getHeaders().containsKey("X-Request-Id")).isTrue();
    }
}
