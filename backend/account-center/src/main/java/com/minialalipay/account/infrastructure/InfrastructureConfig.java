package com.minialalipay.account.infrastructure;

import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
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
     * 异常映射器 Bean。
     */
    @Bean
    public CommonExceptionMapper commonExceptionMapper() {
        return new CommonExceptionMapper();
    }

    /**
     * 请求编号生成器 Bean。
     */
    @Bean
    public RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }

    /**
     * 幂等键校验器 Bean。
     */
    @Bean
    public IdempotencyKeyValidator idempotencyKeyValidator() {
        return new IdempotencyKeyValidator();
    }
}
