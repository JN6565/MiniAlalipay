package com.minialalipay.ai.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
