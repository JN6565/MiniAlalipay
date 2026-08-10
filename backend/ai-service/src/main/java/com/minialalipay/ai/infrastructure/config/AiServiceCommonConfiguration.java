package com.minialalipay.ai.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.common.context.UserContextFilter;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * AI 服务跨子域共享的技术配置，不承载会话、工具或安全领域规则。
 *
 * <p>注册 platform-common 中框架无关组件为 Spring Bean，
 * 供应用层和接口层注入使用。</p>
 */
@Configuration
public class AiServiceCommonConfiguration {

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

    /** @return MCP 工具白名单注册表（领域对象，不含 Spring 依赖） */
    @Bean
    public ToolCatalog toolCatalog() {
        return new ToolCatalog();
    }

    /** @return Jackson ObjectMapper，用于 JSON 序列化/反序列化 */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /** @return UTC 时钟，用于可测试的时间获取 */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
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
     * @return 通用 RestClient 构建器（主 Bean），供 Spring AI 等调用外部互联网地址的组件使用；
     * 自定义的 RestClient.Builder Bean 会使 Boot 默认构建器因 {@code @ConditionalOnMissingBean}
     * 退让，若不补此普通构建器，Spring AI 将注入到负载均衡构建器，把 api.deepseek.com 等
     * 外部域名当作服务名经 Nacos 解析而失败；内部服务名调用必须显式使用 {@link LoadBalanced} 限定符
     */
    @Bean
    @Primary
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * @return 带负载均衡的 RestClient 构建器，用于经网关调用其他微服务；
     * Spring Cloud LoadBalancer 只装饰标注 {@link LoadBalanced} 的 RestClient.Builder Bean，
     * 注入方必须使用该限定符，否则服务名 URL 会被当作普通域名走 DNS 解析
     */
    @LoadBalanced
    @Bean
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
