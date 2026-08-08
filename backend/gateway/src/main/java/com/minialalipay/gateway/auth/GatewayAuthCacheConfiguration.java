package com.minialalipay.gateway.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 网关会话校验缓存装配。
 *
 * <p>真实会话适配器与本地演示桩互斥激活，此处按实际存在的适配器装配
 * {@link CachedGatewayAuthenticationPort} 并标记 @Primary，使
 * {@code AuthenticationGlobalFilter} 注入到带 30 秒短 TTL 缓存的版本，
 * 避免连续请求重复远程校验会话。</p>
 */
@Configuration(proxyBeanMethods = false)
public class GatewayAuthCacheConfiguration {

    /** 真实用户中心回源模式下的带缓存认证端口。 */
    @Bean
    @Primary
    @ConditionalOnBean(UserCenterAuthenticationAdapter.class)
    public GatewayAuthenticationPort cachedUserCenterAuthenticationPort(UserCenterAuthenticationAdapter delegate) {
        return new CachedGatewayAuthenticationPort(delegate);
    }

    /** 本地演示桩模式下的带缓存认证端口。 */
    @Bean
    @Primary
    @ConditionalOnBean(DevStubAuthenticationAdapter.class)
    public GatewayAuthenticationPort cachedStubAuthenticationPort(DevStubAuthenticationAdapter delegate) {
        return new CachedGatewayAuthenticationPort(delegate);
    }
}
