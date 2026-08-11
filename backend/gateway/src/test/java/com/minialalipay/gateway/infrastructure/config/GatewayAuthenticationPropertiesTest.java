package com.minialalipay.gateway.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证网关鉴权嵌套配置能够按 application.yml 的路径绑定。
 */
class GatewayAuthenticationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "gateway.authentication.user-center-uri=http://localhost:8081",
                    "gateway.authentication.service-token=test-service-token",
                    "gateway.authentication.stub.enabled=true",
                    "gateway.authentication.stub.token=test-token",
                    "gateway.authentication.stub.principal-id=test-user",
                    "gateway.authentication.stub.roles=USER,ADMIN");

    @Test
    void 应按嵌套路径绑定用户中心和演示桩配置() {
        contextRunner.run(context -> {
            GatewayAuthenticationProperties properties = context.getBean(GatewayAuthenticationProperties.class);

            assertThat(properties.getUserCenterUri()).isEqualTo("http://localhost:8081");
            assertThat(properties.getServiceToken()).isEqualTo("test-service-token");
            assertThat(properties.getStub().isEnabled()).isTrue();
            assertThat(properties.getStub().getToken()).isEqualTo("test-token");
            assertThat(properties.getStub().getPrincipalId()).isEqualTo("test-user");
            assertThat(properties.getStub().getRoles()).isEqualTo("USER,ADMIN");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GatewayAuthenticationProperties.class)
    static class PropertiesConfiguration {
    }
}
