package com.minialalipay.account.infrastructure;

import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

/**
 * 基础设施层 Spring 配置。
 *
 * <p>注册 platform-common 中框架无关组件为 Spring Bean，
 * 供应用层和接口层注入使用。</p>
 */
@Configuration
public class InfrastructureConfig {

    /**
     * 幂等键校验器 Bean。
     */
    @Bean
    public IdempotencyKeyValidator idempotencyKeyValidator() {
        return new IdempotencyKeyValidator();
    }
}
