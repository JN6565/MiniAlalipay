package com.minialalipay.business.infrastructure.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.business.application.monitoring.DashboardSummary.ServiceHealth;
import com.minialalipay.business.application.monitoring.DashboardSummary.ServiceHealthStatus;
import com.minialalipay.business.application.port.ServiceHealthProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 基于 Actuator 和 Redis PING 的服务健康探针。
 *
 * <p>探针仅在运营看板请求期间执行，HTTP 端点必须是服务内部配置的健康地址。
 * HTTP 正常响应但未返回 {@code UP} 时标为 DOWN；超时、网络错误或非预期响应标为 UNKNOWN，
 * 避免把无法取得证据误报为服务故障。</p>
 */
@Component
public class ActuatorServiceHealthProbe implements ServiceHealthProbe {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient client;
    private final RedisConnectionFactory redisConnectionFactory;
    private final String gatewayHealthUrl;
    private final String accountCenterHealthUrl;
    private final String aiServiceHealthUrl;
    private final Duration timeout;

    /** 创建服务健康探针，健康地址与超时时间均可由部署环境覆盖。 */
    public ActuatorServiceHealthProbe(RedisConnectionFactory redisConnectionFactory,
                                      @Value("${minialalipay.monitoring.health.gateway-url:http://localhost:8080/actuator/health}") String gatewayHealthUrl,
                                      @Value("${minialalipay.monitoring.health.account-center-url:http://localhost:8083/actuator/health}") String accountCenterHealthUrl,
                                      @Value("${minialalipay.monitoring.health.ai-service-url:http://localhost:8084/actuator/health}") String aiServiceHealthUrl,
                                      @Value("${minialalipay.monitoring.health.timeout-ms:800}") long timeoutMs) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.gatewayHealthUrl = gatewayHealthUrl;
        this.accountCenterHealthUrl = accountCenterHealthUrl;
        this.aiServiceHealthUrl = aiServiceHealthUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.client = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    /** 查询网关、账户中心、Redis 与 AI 服务的当前健康状态。 */
    @Override
    public List<ServiceHealth> probeAll() {
        return List.of(
                probeHttp("gateway", "网关 gateway", gatewayHealthUrl),
                probeHttp("account-center", "账户中心 account-center", accountCenterHealthUrl),
                probeRedis(),
                probeHttp("ai-service", "AI 服务 ai-service", aiServiceHealthUrl));
    }

    private ServiceHealth probeHttp(String serviceCode, String serviceName, String targetUrl) {
        Instant startedAt = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(targetUrl)).GET().timeout(timeout).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = Duration.between(startedAt, Instant.now()).toMillis();
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return health(serviceCode, serviceName, ServiceHealthStatus.UNKNOWN, latency);
            }
            JsonNode body = JSON.readTree(response.body());
            ServiceHealthStatus status = "UP".equals(body.path("status").asText())
                    ? ServiceHealthStatus.UP : ServiceHealthStatus.DOWN;
            return health(serviceCode, serviceName, status, latency);
        } catch (Exception ignored) {
            return health(serviceCode, serviceName, ServiceHealthStatus.UNKNOWN, null);
        }
    }

    private ServiceHealth probeRedis() {
        Instant startedAt = Instant.now();
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            long latency = Duration.between(startedAt, Instant.now()).toMillis();
            return health("redis", "Redis 缓存", "PONG".equalsIgnoreCase(pong)
                    ? ServiceHealthStatus.UP : ServiceHealthStatus.DOWN, latency);
        } catch (Exception ignored) {
            return health("redis", "Redis 缓存", ServiceHealthStatus.UNKNOWN, null);
        }
    }

    private static ServiceHealth health(String serviceCode, String serviceName, ServiceHealthStatus status,
                                        Long latencyMs) {
        return new ServiceHealth(serviceCode, serviceName, status, latencyMs, Instant.now());
    }
}
