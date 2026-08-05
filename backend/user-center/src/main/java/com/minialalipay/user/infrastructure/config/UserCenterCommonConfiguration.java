package com.minialalipay.user.infrastructure.config;

import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 用户中心共享的技术配置，不承载用户、身份、会话或支付密码领域规则。
 *
 * <p>注册平台公共模块提供的 Bean，包括：
 * <ul>
 *   <li>{@link CommonExceptionMapper} - 框架无关的公共异常映射器</li>
 *   <li>{@link RequestIdGenerator} - 安全解析或生成请求编号的工具</li>
 *   <li>{@link IdempotencyKeyValidator} - 校验幂等键格式的工具</li>
 * </ul>
 * </p>
 */
@Configuration
public class UserCenterCommonConfiguration {

    /**
     * 注册框架无关的公共异常映射器。
     *
     * <p>将 {@link com.minialalipay.common.error.BusinessException} 转换为
     * 带正确 HTTP 状态码的 {@link com.minialalipay.common.api.ApiResponse}。</p>
     *
     * @return 公共异常映射器实例
     */
    @Bean
    public CommonExceptionMapper commonExceptionMapper() {
        return new CommonExceptionMapper();
    }

    /**
     * 注册安全解析或生成请求编号的工具。
     *
     * <p>解析客户端请求编号（格式 {@code [A-Za-z0-9._:-]{1,128}}），
     * 缺失或不安全时生成 {@code req_<UUID>} 格式的请求编号。</p>
     *
     * @return 请求编号生成器实例
     */
    @Bean
    public RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }

    /**
     * 注册校验幂等键格式的工具。
     *
     * <p>校验对外写接口使用的幂等键格式（{@code [A-Za-z0-9._:-]{16,64}}），
     * 不承担业务幂等记录的持久化职责。</p>
     *
     * @return 幂等键校验器实例
     */
    @Bean
    public IdempotencyKeyValidator idempotencyKeyValidator() {
        return new IdempotencyKeyValidator();
    }

    /**
     * 注册 RestTemplate，用于调用其他微服务。
     *
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
