package com.minialalipay.gateway;

import com.minialalipay.common.trace.RequestIdGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }
}
