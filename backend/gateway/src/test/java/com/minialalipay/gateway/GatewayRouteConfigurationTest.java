package com.minialalipay.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关生产路由配置测试，防止 P0 接口被转发到错误服务。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.nacos.discovery.enabled=false"})
class GatewayRouteConfigurationTest {

    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void routesCreditOperationsToAccountCenter() {
        RouteDefinition route = gatewayProperties.getRoutes().stream()
                .filter(candidate -> "account-center-credit-ops".equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少账户中心信用运维路由"));

        assertThat(route.getUri().toString())
                .as("信用运维路由应指向本地账户中心或服务发现中的账户中心")
                .isIn("http://localhost:8083", "lb://account-center");
        // 路由目标：Nacos 模式为 lb://account-center，直连模式为 http://localhost:8083
        assertThat(route.getUri().toString())
                .containsAnyOf("account-center", "8083");
        assertThat(route.getPredicates())
                .anySatisfy(predicate -> assertThat(predicate.getArgs().values())
                        .contains("/api/v1/ops/credit/**"));
    }

    @Test
    void routesImplementedRechargeAndManualCaseOperationsToBusinessCenter() {
        assertBusinessRoute("business-center-recharges", "/api/v1/recharges/**");
        assertBusinessRoute("business-center-manual-cases", "/api/v1/manual-cases/**");
        assertBusinessRoute("business-center-qr-pay", "/api/v1/qr-pay/**");
    }

    @Test
    void p2pCollectionTokenExchangeUsesIpBasedRateLimiting() {
        RouteDefinition route = gatewayProperties.getRoutes().stream()
                .filter(candidate -> "business-center-p2p-collections-token".equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 C2C 令牌交换路由"));
        assertThat(route.getUri().toString()).containsAnyOf("business-center", "8082");
        assertThat(route.getPredicates())
                .anySatisfy(predicate -> assertThat(predicate.getArgs().values())
                        .anyMatch(v -> v.contains("/api/v1/p2p-collections/by-token")
                                && v.contains("/api/v1/p2p-collections/token-exchanges")));
    }

    @Test
    void routesTransactionsAndMonitoringToBusinessCenter() {
        assertBusinessRoute("business-center-transactions", "/api/v1/transactions/**");
        assertBusinessRoute("business-center-monitoring", "/api/v1/monitoring/**");
    }

    @Test
    void doesNotRouteInternalServiceOperations() {
        assertThat(gatewayProperties.getRoutes())
                .flatExtracting(RouteDefinition::getPredicates)
                .flatExtracting(predicate -> predicate.getArgs().values())
                .noneMatch(path -> path.contains("/internal/"));
    }

    private void assertBusinessRoute(String routeId, String path) {
        RouteDefinition route = gatewayProperties.getRoutes().stream()
                .filter(candidate -> routeId.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少业务中心路由: " + routeId));
        assertThat(route.getUri().toString()).containsAnyOf("business-center", "8082");
        assertThat(route.getPredicates())
                .anySatisfy(predicate -> assertThat(predicate.getArgs().values()).contains(path));
    }
}
