package com.minialalipay.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/**
 * Redis 限流依赖故障集成测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
@EnabledIfSystemProperty(named = "minialalipay.redis.failure.integration", matches = "true")
class GatewayRedisFailureIntegrationTest {

    private static final DisposableServer USER_SERVER = HttpServer.create()
            .port(0)
            .handle((request, response) -> response.sendString(Mono.just("{\"status\":\"UP\"}")))
            .bindNow();

    @Autowired
    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void failureProperties(DynamicPropertyRegistry registry) {
        registry.add("USER_CENTER_URI", () -> "http://127.0.0.1:" + USER_SERVER.port());
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> "1");
        registry.add("spring.data.redis.timeout", () -> "200ms");
    }

    @Test
    @DisplayName("Redis 不可连接时限流器临时放行而不是返回 500")
    void unavailableRedisDoesNotRejectAllTraffic() {
        webTestClient.get()
                .uri("/api/v1/auth/login")
                .header("X-Forwarded-For", "203.0.113.20")
                .header("X-Request-Id", "request-redis-unavailable")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-Id", "request-redis-unavailable");
    }

    @AfterAll
    static void stopDownstream() {
        USER_SERVER.disposeNow();
    }
}
