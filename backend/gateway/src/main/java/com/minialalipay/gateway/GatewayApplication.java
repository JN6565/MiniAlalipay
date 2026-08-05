package com.minialalipay.gateway;

import com.minialalipay.common.trace.RequestIdGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import com.minialalipay.gateway.auth.GatewayAuthenticationProperties;
import com.minialalipay.gateway.config.CorsProperties;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({GatewayAuthenticationProperties.class, CorsProperties.class})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }
}
