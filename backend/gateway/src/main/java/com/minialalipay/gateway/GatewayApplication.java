package com.minialalipay.gateway;

import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.gateway.infrastructure.config.CorsProperties;
import com.minialalipay.gateway.infrastructure.config.GatewayAuthenticationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * MiniAlalipay API 网关启动入口。
 *
 * <p>作为系统的唯一外部接入点（端口 8080），负责接收所有前端、H5 和 MCP 外部请求，
 * 完成鉴权、CSRF/CORS、限流、链路追踪、安全响应头和协议级错误转换后，
 * 通过 Spring Cloud Gateway 路由到对应的下游服务。</p>
 *
 * <h3>注册的 Bean</h3>
 * <ul>
 *   <li>{@link com.minialalipay.common.trace.RequestIdGenerator} — 请求编号生成器</li>
 * </ul>
 *
 * <h3>启用的配置属性</h3>
 * <ul>
 *   <li>{@link com.minialalipay.gateway.infrastructure.config.CorsProperties} — CORS 跨域配置</li>
 *   <li>{@link com.minialalipay.gateway.infrastructure.config.GatewayAuthenticationProperties} — 鉴权配置</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties({CorsProperties.class, GatewayAuthenticationProperties.class})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    RequestIdGenerator requestIdGenerator() {
        return new RequestIdGenerator();
    }
}
