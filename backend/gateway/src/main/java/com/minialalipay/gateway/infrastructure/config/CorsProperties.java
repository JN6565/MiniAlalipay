package com.minialalipay.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 网关 CORS 配置属性。
 *
 * <p>允许在生产部署时通过环境变量覆盖开发来源，避免硬编码地址进入制品。
 * 默认值仅适用于本地开发基线。</p>
 */
@ConfigurationProperties(prefix = "gateway.cors")
public class CorsProperties {

    private List<String> allowedOrigins = List.of("http://localhost:8000", "http://localhost:8001");
    private long maxAgeSeconds = 3600L;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public long getMaxAgeSeconds() {
        return maxAgeSeconds;
    }

    public void setMaxAgeSeconds(long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
    }
}
