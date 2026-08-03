package com.minialalipay.ai;

import com.minialalipay.common.trace.RequestIdGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }

    @Bean
    RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }
}
