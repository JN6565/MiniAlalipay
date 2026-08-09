package com.minialalipay.business.infrastructure.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.business.application.monitoring.DashboardSummary.ServiceHealth;
import com.minialalipay.business.application.monitoring.DashboardSummary.ServiceHealthStatus;
import com.minialalipay.business.application.port.ServiceHealthProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 基于 Actuator 和 Redis PING 的服务健康探针。
 *
 * <p>探针仅在运营看板请求期间执行，HTTP 端点必须是服务内部配置的健康地址。
 * HTTP 正常响应但未返回 {@code UP} 时标为 DOWN；超时、网络错误或非预期响应标为 UNKNOWN，
 * 避免把无法取得证据误报为服务故障。</p>
 *
 * <p>健康地址默认使用服务名（如 {@code http://account-center/actuator/health}），注入的
 * {@link RestClient.Builder} 必须标注 {@link LoadBalanced} 才会被 Spring Cloud LoadBalancer 装饰，
 * 经 Nacos 解析实例；未装饰的构建器会把服务名当作域名走 DNS 解析。关闭负载均衡后
 * 拦截器不可用，探针请求统一标为 UNKNOWN，测试可通过覆盖健康地址为直连 stub 规避。</p>
 */
@Component
public class ActuatorServiceHealthProbe implements ServiceHealthProbe {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;
    private final RedisConnectionFactory redisConnectionFactory;
    private final String gatewayHealthUrl;
    private final String accountCenterHealthUrl;
    private final String aiServiceHealthUrl;

    /** 创建服务健康探针，健康地址与超时时间均可由部署环境覆盖。 */
    public ActuatorServiceHealthProbe(RedisConnectionFactory redisConnectionFactory,
                                      @LoadBalanced RestClient.Builder restClientBuilder,
                                      @Value("${minialalipay.monitoring.health.gateway-url:http://minialalipay-gateway/actuator/health}") String gatewayHealthUrl,
                                      @Value("${minialalipay.monitoring.health.account-center-url:http://account-center/actuator/health}") String accountCenterHealthUrl,
                                      @Value("${minialalipay.monitoring.health.ai-service-url:http://ai-service/actuator/health}") String aiServiceHealthUrl,
                                      @Value("${minialalipay.monitoring.health.timeout-ms:800}") long timeoutMs) {
        this.redisConnectionFactory = redisConnectionFactory;
        this.gatewayHealthUrl = gatewayHealthUrl;
        this.accountCenterHealthUrl = accountCenterHealthUrl;
        this.aiServiceHealthUrl = aiServiceHealthUrl;
        // 探针超时属于看板读取路径，连接与读取超时一致，避免看板请求被单个不健康服务拖慢。
        Duration timeout = Duration.ofMillis(timeoutMs);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.client = restClientBuilder.requestFactory(factory).build();
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
            // RestClient 默认将非 2xx 响应抛异常，统一归入 catch 分支标为 UNKNOWN，
            // 与原探针“非预期响应不算服务故障”的语义保持一致。
            String body = client.get().uri(targetUrl).retrieve().body(String.class);
            long latency = Duration.between(startedAt, Instant.now()).toMillis();
            JsonNode json = JSON.readTree(body);
            ServiceHealthStatus status = "UP".equals(json.path("status").asText())
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
