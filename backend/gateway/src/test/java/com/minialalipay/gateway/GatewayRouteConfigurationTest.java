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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRouteConfigurationTest {

    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void routesCreditOperationsToAccountCenter() {
        RouteDefinition route = gatewayProperties.getRoutes().stream()
                .filter(candidate -> "account-center-credit-ops".equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少账户中心信用运维路由"));

        assertThat(route.getUri().toString()).contains("8083");
        assertThat(route.getPredicates())
                .anySatisfy(predicate -> assertThat(predicate.getArgs().values())
                        .contains("/api/v1/ops/credit/**"));
    }
}
