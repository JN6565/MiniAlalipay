package com.minialalipay.account.infrastructure.config;

import com.minialalipay.common.context.UserContextFilter;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 账户中心跨子域共享的技术配置，不承载余额、信用或账本领域规则。
 *
 * <p>注册 platform-common 中框架无关组件为 Spring Bean，
 * 供应用层和接口层注入使用。</p>
 */
@Configuration
public class AccountCenterCommonConfiguration {

    /** @return 框架无关的公共异常映射器 */
    @Bean
    public CommonExceptionMapper commonExceptionMapper() {
        return new CommonExceptionMapper();
    }

    /** @return 安全解析或生成请求编号的工具 */
    @Bean
    public RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }

    /** @return 幂等键格式校验器，用于写操作接口校验客户端幂等键 */
    @Bean
    public IdempotencyKeyValidator idempotencyKeyValidator() {
        return new IdempotencyKeyValidator();
    }

    /**
     * 注册用户上下文过滤器，从网关透传头提取用户身份写入 ThreadLocal。
     */
    @Bean
    public FilterRegistrationBean<UserContextFilter> userContextFilter() {
        FilterRegistrationBean<UserContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new UserContextFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        registration.setName("userContextFilter");
        return registration;
    }
}
