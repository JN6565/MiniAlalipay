package com.minialalipay.gateway.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关鉴权配置。
 *
 * <p>统一绑定 {@code gateway.authentication} 下的配置，确保本地演示桩和
 * 用户中心回源认证读取同一套属性路径。服务令牌仅用于网关到用户中心的内部调用，
 * 不得通过前端请求传入。</p>
 */
@ConfigurationProperties(prefix = "gateway.authentication")
public class GatewayAuthenticationProperties {

    /** 用户中心会话校验服务地址，可使用 {@code lb://user-center}。 */
    private String userCenterUri = "lb://user-center";

    /** 网关调用用户中心内部接口时使用的服务令牌。 */
    private String serviceToken = "local-internal-token";

    /** 本地演示鉴权桩配置。 */
    private StubProperties stub = new StubProperties();

    public String getUserCenterUri() {
        return userCenterUri;
    }

    public void setUserCenterUri(String userCenterUri) {
        this.userCenterUri = userCenterUri;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }

    public StubProperties getStub() {
        return stub;
    }

    public void setStub(StubProperties stub) {
        this.stub = stub;
    }

    /** 本地演示鉴权桩的受控身份配置。 */
    public static class StubProperties {

        /** 是否启用本地演示鉴权桩。 */
        private boolean enabled;

        /** 演示令牌原文，仅允许通过受控开发配置注入。 */
        private String token = "dev-admin-token";

        /** 演示认证主体标识。 */
        private String principalId = "dev-admin";

        /** 演示主体角色，多个角色使用逗号分隔。 */
        private String roles = "ADMIN";

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

        public String getRoles() {
            return roles;
        }

        public void setRoles(String roles) {
            this.roles = roles;
        }
    }
}
