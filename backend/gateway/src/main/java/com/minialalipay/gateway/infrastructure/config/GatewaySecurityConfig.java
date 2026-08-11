package com.minialalipay.gateway.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * 网关 Spring Security 配置。
 *
 * <p>使用 Spring Security 作为标准安全框架骨架，实际认证仍由
 * {@link com.minialalipay.gateway.interfaces.filter.AuthenticationGlobalFilter} 处理。
 * 此处仅配置基础设施级安全策略（禁用 CSRF、无状态 Session、全路径放行），
 * 不在此处编写认证规则——认证逻辑归属于网关 Filter 链。</p>
 *
 * <h3>与自定义 Filter 的分工</h3>
 * <ul>
 *   <li>Spring Security：提供安全上下文传播、框架级 CSRF 禁用、响应头安全增强</li>
 *   <li>AuthenticationGlobalFilter：Bearer Token 提取、会话校验、角色门禁、身份头注入</li>
 *   <li>CsrfGlobalFilter：Cookie 会话 CSRF Token 语法校验</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                // 网关已有 CsrfGlobalFilter 专门处理 CSRF，禁用 Spring Security 默认 CSRF
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 全路径放行——实际认证由 AuthenticationGlobalFilter 在 Filter 链中处理
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                // 无状态 Session——不创建 JSESSIONID
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .build();
    }
}
