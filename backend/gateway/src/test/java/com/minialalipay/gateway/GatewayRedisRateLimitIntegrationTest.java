package com.minialalipay.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关 Redis 真实限流集成测试。
 *
 * <p>默认不依赖外部环境；阶段验收时通过系统属性显式开启，连接本机 Redis，
 * 验证令牌桶放行和拒绝响应。测试使用唯一 IP 键，不清理或覆盖已有 Redis 数据。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
@EnabledIfSystemProperty(named = "minialalipay.redis.integration", matches = "true")
class GatewayRedisRateLimitIntegrationTest {

    private static final DisposableServer USER_SERVER = HttpServer.create()
            .port(0)
            .handle((request, response) -> response.sendString(Mono.just("{\"status\":\"UP\"}")))
            .bindNow();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void integrationProperties(DynamicPropertyRegistry registry) {
        registry.add("USER_CENTER_URI", () -> "http://127.0.0.1:" + USER_SERVER.port());
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "6379");
        registry.add("spring.data.redis.password", () -> "");
    }

    @Test
    @DisplayName("真实 Redis 令牌桶先放行并在超出容量后返回统一 429 响应")
    void redisRateLimiterAllowsThenRejectsWithStandardResponse() {
        String testIp = "198.51.100." + (Math.abs(UUID.randomUUID().hashCode()) % 200 + 1);
        boolean allowed = false;
        EntityExchangeResult<byte[]> rejected = null;

        for (int index = 0; index < 80 && rejected == null; index++) {
            EntityExchangeResult<byte[]> result = webTestClient.get()
                    .uri("/api/v1/auth/login")
                    .header("X-Forwarded-For", testIp)
                    .header("X-Request-Id", "request-rate-limit-" + index)
                    .exchange()
                    .expectBody()
                    .returnResult();
            if (result.getStatus() == HttpStatus.OK) {
                allowed = true;
            } else if (result.getStatus() == HttpStatus.TOO_MANY_REQUESTS) {
                rejected = result;
            }
        }

        assertThat(allowed).isTrue();
        assertThat(rejected).as("超出令牌桶容量后应出现 429").isNotNull();
        String body = new String(rejected.getResponseBody(), StandardCharsets.UTF_8);
        assertThat(body)
                .contains("RATE_LIMITED")
                .contains("请求过于频繁")
                .contains("request-rate-limit-");
    }

    @AfterAll
    static void stopDownstream() {
        USER_SERVER.disposeNow();
    }
}
