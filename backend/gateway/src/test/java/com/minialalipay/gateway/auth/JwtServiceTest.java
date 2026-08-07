package com.minialalipay.gateway.auth;

import com.minialalipay.gateway.filter.GatewayAuthContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 签发与校验服务测试。
 *
 * <p>覆盖签发、校验、过期模拟和签名篡改检测。</p>
 */
class JwtServiceTest {

    /** 使用固定密钥确保测试可重复；HS256 要求密钥至少 256 位（32 字节），此处取足长固定串。 */
    private final JwtService jwtService = new JwtService("test-secret-key-for-unit-test-0123456789");

    @Test
    @DisplayName("签发并校验有效 JWT 返回正确的认证上下文")
    void createAndValidateReturnsCorrectContext() {
        String token = jwtService.createToken("user-001", Set.of("USER", "OPERATOR"));
        assertThat(token).isNotNull();

        GatewayAuthContext context = jwtService.validateToken(token);
        assertThat(context).isNotNull();
        assertThat(context.principalId()).isEqualTo("user-001");
        assertThat(context.roles()).containsExactlyInAnyOrder("USER", "OPERATOR");
    }

    @Test
    @DisplayName("null 令牌返回 null")
    void nullTokenReturnsNull() {
        assertThat(jwtService.validateToken(null)).isNull();
    }

    @Test
    @DisplayName("空字符串令牌返回 null")
    void blankTokenReturnsNull() {
        assertThat(jwtService.validateToken("")).isNull();
        assertThat(jwtService.validateToken("   ")).isNull();
    }

    @Test
    @DisplayName("签名被篡改的 JWT 返回 null")
    void tamperedTokenReturnsNull() {
        String token = jwtService.createToken("user-001", Set.of("USER"));
        // 篡改签名段首字符：base64url 末位含填充位，改末位可能解码出相同字节导致校验意外通过；
        // 改首字符必然改变签名字节，保证断言不依赖 iat 时间的随机性。
        int signatureStart = token.lastIndexOf('.');
        String tampered = token.substring(0, signatureStart + 1) + "X" + token.substring(signatureStart + 2);
        assertThat(jwtService.validateToken(tampered)).isNull();
    }

    @Test
    @DisplayName("不同密钥签发的 JWT 校验失败")
    void differentKeyTokenValidationFails() {
        JwtService otherService = new JwtService("different-secret-key");
        String token = otherService.createToken("user-001", Set.of("USER"));

        assertThat(jwtService.validateToken(token)).isNull();
    }

    @Test
    @DisplayName("自定义 TTL 的 JWT 签发成功")
    void customTtlTokenCreated() {
        String token = jwtService.createToken("user-002", Set.of("USER"), 10);
        assertThat(token).isNotNull();

        GatewayAuthContext context = jwtService.validateToken(token);
        assertThat(context).isNotNull();
        assertThat(context.principalId()).isEqualTo("user-002");
    }

    @Test
    @DisplayName("空角色集合的 JWT 正常签发和校验")
    void emptyRolesTokenWorks() {
        String token = jwtService.createToken("user-003", Set.of());
        assertThat(token).isNotNull();

        GatewayAuthContext context = jwtService.validateToken(token);
        assertThat(context).isNotNull();
        assertThat(context.principalId()).isEqualTo("user-003");
        assertThat(context.roles()).isEmpty();
    }

    @Test
    @DisplayName("包含特殊字符的用户 ID 正常签发")
    void specialCharacterUserIdWorks() {
        String token = jwtService.createToken("dev-user-001_test", Set.of("ADMIN"));
        assertThat(token).isNotNull();

        GatewayAuthContext context = jwtService.validateToken(token);
        assertThat(context).isNotNull();
        assertThat(context.principalId()).isEqualTo("dev-user-001_test");
        assertThat(context.roles()).contains("ADMIN");
    }
}
