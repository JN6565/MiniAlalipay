package com.minialalipay.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 网关跨域资源共享（CORS）配置。
 *
 * <p>仅允许已知前端来源，禁止使用通配符 {@code *}。
 * 支持携带身份凭据，并使用白名单化的请求头和方法。</p>
 *
 * <h3>允许的来源</h3>
 * <ul>
 *   <li>{@code http://localhost:8000} — B 端管理后台</li>
 *   <li>{@code http://localhost:8001} — C 端 H5</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    /** B 端管理后台开发地址。 */
    private static final String ADMIN_ORIGIN = "http://localhost:8000";

    /** C 端 H5 开发地址。 */
    private static final String H5_ORIGIN = "http://localhost:8001";

    /** 允许的 HTTP 方法。 */
    private static final List<String> ALLOWED_METHODS = List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name()
    );

    /** 允许的请求头。 */
    private static final List<String> ALLOWED_HEADERS = List.of(
            HttpHeaders.AUTHORIZATION,
            HttpHeaders.CONTENT_TYPE,
            "X-Request-Id",
            "Idempotency-Key",
            "X-CSRF-Token",
            "Last-Event-ID"
    );

    /** 允许暴露给前端的响应头。 */
    private static final List<String> EXPOSED_HEADERS = List.of(
            "X-Request-Id",
            "Retry-After",
            "Last-Event-ID"
    );

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(ADMIN_ORIGIN, H5_ORIGIN));
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setExposedHeaders(EXPOSED_HEADERS);
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
