package com.minialalipay.business;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 业务中心启动入口。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class BusinessCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessCenterApplication.class, args);
    }
}
