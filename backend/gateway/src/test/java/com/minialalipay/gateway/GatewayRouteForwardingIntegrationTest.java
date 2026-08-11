package com.minialalipay.gateway;

import com.minialalipay.gateway.application.port.GatewayAuthenticationPort;
import com.minialalipay.gateway.application.security.GatewayAuthContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 网关生产路由真实转发集成测试。
 *
 * <p>测试使用随机端口 HTTP 服务模拟四个下游，验证请求确实经过网关转发，
 * 并核对目标服务、认证信息和请求编号是否完整透传。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "10000")
@ActiveProfiles("test")
class GatewayRouteForwardingIntegrationTest {

    private static final String TOKEN = "Bearer test-token-for-integration";
    private static final String REQUEST_ID = "request-stage-two-forwarding";

    private static final DisposableServer USER_SERVER = startDownstream("user-center");
    private static final DisposableServer BUSINESS_SERVER = startDownstream("business-center");
    private static final DisposableServer ACCOUNT_SERVER = startDownstream("account-center");
    private static final DisposableServer AI_SERVER = startDownstream("ai-service");

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private RedisRateLimiter redisRateLimiter;

    @MockBean
    private GatewayAuthenticationPort authenticationPort;

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
        registry.add("USER_CENTER_URI", () -> uri(USER_SERVER));
        registry.add("BUSINESS_CENTER_URI", () -> uri(BUSINESS_SERVER));
        registry.add("ACCOUNT_CENTER_URI", () -> uri(ACCOUNT_SERVER));
        registry.add("AI_SERVICE_URI", () -> uri(AI_SERVER));
    }

    @BeforeEach
    void allowRequestsThroughRateLimiter() {
        when(redisRateLimiter.isAllowed(anyString(), anyString()))
                .thenReturn(Mono.just(new RedisRateLimiter.Response(true, Map.of())));
        // 认证端口 Mock：接受任意非空令牌，返回测试用 ADMIN 用户，同时覆盖通用运维路径与
        // 仅系统管理员的信用运维路径（/api/v1/ops/credit/**）转发场景。
        when(authenticationPort.authenticate(anyString()))
                .thenReturn(Mono.just(new GatewayAuthContext("dev-user-001",
                        Set.of("USER", "OPERATOR", "ADMIN"))));
    }

    @ParameterizedTest(name = "{0} 转发到 {1}")
    @MethodSource("routeCases")
    @DisplayName("P0 路径转发到所属服务并透传请求上下文")
    void routesRequestToExpectedService(String path, String expectedService) {
        webTestClient.get()
                .uri(path)
                .header("Authorization", TOKEN)
                .header("X-Request-Id", REQUEST_ID)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Downstream-Service", expectedService)
                .expectHeader().valueEquals("X-Downstream-Authorization", TOKEN)
                .expectHeader().valueEquals("X-Downstream-Request-Id", REQUEST_ID)
                .expectHeader().valueEquals("X-Request-Id", REQUEST_ID);
    }

    static Stream<Arguments> routeCases() {
        return Stream.of(
                Arguments.of("/api/v1/users/stage-two", "user-center"),
                Arguments.of("/api/v1/transfers/stage-two", "business-center"),
                Arguments.of("/api/v1/recharges/stage-two", "business-center"),
                Arguments.of("/api/v1/manual-cases/stage-two", "business-center"),
                Arguments.of("/api/v1/qr-pay/orders/stage-two", "business-center"),
                Arguments.of("/api/v1/accounts/stage-two", "account-center"),
                Arguments.of("/api/v1/agent/stage-two", "ai-service"),
                Arguments.of("/api/v1/ops/credit/stage-two", "account-center")
        );
    }

    @AfterAll
    static void stopDownstreams() {
        USER_SERVER.disposeNow();
        BUSINESS_SERVER.disposeNow();
        ACCOUNT_SERVER.disposeNow();
        AI_SERVER.disposeNow();
    }

    private static DisposableServer startDownstream(String serviceName) {
        return HttpServer.create()
                .port(0)
                .handle((request, response) -> response
                        .header("X-Downstream-Service", serviceName)
                        .header("X-Downstream-Authorization", request.requestHeaders().get("Authorization"))
                        .header("X-Downstream-Request-Id", request.requestHeaders().get("X-Request-Id"))
                        .sendString(Mono.just("{\"status\":\"UP\"}")))
                .bindNow();
    }

    private static String uri(DisposableServer server) {
        return "http://127.0.0.1:" + server.port();
    }
}
