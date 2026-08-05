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
 * 支持携带身份凭据，并使用白名单化的请求头和方法。
 * 来源列表通过 {@link CorsProperties} 外部化，默认值为本地开发地址。</p>
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

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
            "X-Trace-Id",
            "Retry-After",
            "Last-Event-ID"
    );

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : corsProperties.getAllowedOrigins()) {
            config.addAllowedOrigin(origin);
        }
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setExposedHeaders(EXPOSED_HEADERS);
        config.setAllowCredentials(true);
        config.setMaxAge(corsProperties.getMaxAgeSeconds());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
