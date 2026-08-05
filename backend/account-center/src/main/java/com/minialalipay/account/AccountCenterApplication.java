package com.minialalipay.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 账户中心启动入口。
 *
 * <p>本地开发不需要服务发现功能，如需启用 Nacos/Eureka 服务注册，
 * 请添加 Spring Cloud 依赖并取消 {@code @EnableDiscoveryClient} 注解。</p>
 */
@SpringBootApplication
public class AccountCenterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountCenterApplication.class, args);
    }
}
