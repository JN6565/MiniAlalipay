package com.minialalipay.account.infrastructure.config;

import com.minialalipay.common.context.UserContextFilter;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

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

    /**
     * @return 带负载均衡的 RestTemplate，用于调用其他微服务（如 user-center）；
     * URL 主机名（如 http://user-center）经 Spring Cloud LoadBalancer 解析为 Nacos 实例
     */
    @LoadBalanced
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * @return 带负载均衡的 RestClient 构建器，用于调用其他微服务（如 user-center）；
     * Spring Cloud LoadBalancer 只装饰标注 {@link LoadBalanced} 的 RestClient.Builder Bean，
     * 注入方必须使用该限定符，否则服务名 URL 会被当作普通域名走 DNS 解析
     */
    @LoadBalanced
    @Bean
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
