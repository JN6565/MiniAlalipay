package com.minialalipay.gateway.infrastructure.auth;

import com.minialalipay.gateway.application.security.GatewayAuthContext;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 本地演示鉴权桩测试：只有令牌完全匹配才返回受控身份，其余令牌一律拒绝，
 * 且身份仅来自受控配置，不读取客户端身份字段。
 */
class DevStubAuthenticationAdapterTest {

    @Test
    void 匹配令牌返回受控身份与角色() {
        DevStubAuthenticationAdapter adapter = new DevStubAuthenticationAdapter(
                "dev-admin-token", "dev-admin", "ADMIN,OPERATOR");

        GatewayAuthContext context = adapter.authenticate("dev-admin-token").block();

        assertThat(context).isNotNull();
        assertThat(context.principalId()).isEqualTo("dev-admin");
        assertThat(context.roles()).isEqualTo(Set.of("ADMIN", "OPERATOR"));
    }

    @Test
    void 不匹配令牌一律拒绝() {
        DevStubAuthenticationAdapter adapter = new DevStubAuthenticationAdapter(
                "dev-admin-token", "dev-admin", "ADMIN");

        Mono<GatewayAuthContext> result = adapter.authenticate("forged-token");

        assertThat(result.blockOptional()).isEmpty();
    }

    @Test
    void 角色默认仅系统管理员且空白项被过滤() {
        DevStubAuthenticationAdapter adapter = new DevStubAuthenticationAdapter(
                "dev-admin-token", "dev-admin", "ADMIN,, ");

        GatewayAuthContext context = adapter.authenticate("dev-admin-token").block();

        assertThat(context.roles()).isEqualTo(Set.of("ADMIN"));
    }
}
