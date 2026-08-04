package com.minialalipay.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 账户中心启动入口。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AccountCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountCenterApplication.class, args);
    }
}
