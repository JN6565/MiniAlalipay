package com.minialalipay.gateway.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 网关阶段二受控鉴权配置。
 *
 * <p>当前用户中心尚未提供会话校验契约，因此只允许显式启用的开发 Stub。
 * 未启用或令牌未精确匹配时必须关闭失败。</p>
 */
@ConfigurationProperties(prefix = "gateway.authentication.stub")
public class GatewayAuthenticationProperties {

    private boolean enabled;
    private String token = "";
    private String principalId = "dev-user-001";
    private Set<String> roles = new LinkedHashSet<>(Set.of("USER"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public Set<String> getRoles() {
        return Set.copyOf(roles);
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles == null ? Set.of() : new LinkedHashSet<>(roles);
    }
}
