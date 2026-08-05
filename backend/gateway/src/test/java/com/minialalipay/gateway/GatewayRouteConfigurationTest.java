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
        assertThat(route.getPredicates())
                .anySatisfy(predicate -> assertThat(predicate.getArgs().values())
                        .contains("/api/v1/ops/credit/**"));
    }

    @Test
    void doesNotRouteInternalServiceOperations() {
        assertThat(gatewayProperties.getRoutes())
                .flatExtracting(RouteDefinition::getPredicates)
                .flatExtracting(predicate -> predicate.getArgs().values())
                .noneMatch(path -> path.contains("/internal/"));
    }
}
