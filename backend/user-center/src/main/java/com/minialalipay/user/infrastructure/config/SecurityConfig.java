package com.minialalipay.user.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置。
 *
 * <p>禁用默认的登录页面和 CSRF 保护，允许所有请求匿名访问。
 * 实际的身份验证由网关和自定义的会话管理器处理。</p>
 *
 * <p>配置说明：
 * <ul>
 *   <li>禁用 CSRF 保护 - 因为使用 JWT 令牌而不是 Cookie</li>
 *   <li>禁用默认登录页面 - 使用自定义的登录接口</li>
 *   <li>允许所有请求匿名访问 - 身份验证由网关处理</li>
 *   <li>禁用 Session - 使用无状态的 JWT 令牌</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置安全过滤器链。
     *
     * <p>禁用 Spring Security 的默认行为，允许所有请求匿名访问。
     * 实际的身份验证由网关和自定义的会话管理器处理。</p>
     *
     * @param http HTTP 安全配置
     * @return 安全过滤器链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF 保护
                .csrf(AbstractHttpConfigurer::disable)
                // 允许所有请求匿名访问
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                // 禁用默认登录页面
                .formLogin(AbstractHttpConfigurer::disable)
                // 禁用默认登出页面
                .logout(AbstractHttpConfigurer::disable)
                // 使用无状态 Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
