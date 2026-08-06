package com.minialalipay.gateway.auth;

import com.minialalipay.gateway.filter.GatewayAuthContext;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

/**
 * 网关内部 JWT 签发与校验服务。
 *
 * <p>JWT 不作为外部认证令牌（外部仍使用不透明 Session Token），
 * 仅用于网关内部缓存用户中心会话校验结果，减少对用户中心的重复调用。</p>
 *
 * <h3>JWT 声明</h3>
 * <ul>
 *   <li>{@code sub} — userId，认证主体标识</li>
 *   <li>{@code roles} — 逗号分隔的角色集合</li>
 *   <li>{@code iat} — 签发时间</li>
 *   <li>{@code exp} — 过期时间（默认 5 分钟）</li>
 * </ul>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>使用 HMAC-SHA256 签名，密钥通过 {@code gateway.jwt.secret} 配置</li>
 *   <li>默认密钥在启动时随机生成——重启后所有已签发 JWT 自动失效</li>
 *   <li>JWT 不写入日志、URL 或持久化存储</li>
 * </ul>
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** JWT 签发角色声明的键名。 */
    static final String CLAIM_ROLES = "roles";

    /** JWT 默认有效期（分钟）。 */
    private static final int DEFAULT_TTL_MINUTES = 5;

    private final byte[] secret;

    public JwtService(@Value("${gateway.jwt.secret:#{T(java.util.UUID).randomUUID().toString()}}") String secret) {
        this.secret = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        log.info("JWT 签名密钥已初始化（长度 {} 字节）", this.secret.length);
    }

    /**
     * 签发内部 JWT。
     *
     * @param userId    认证主体标识
     * @param roles     角色集合
     * @param ttlMinutes 有效期（分钟）
     * @return 签发的 JWT 字符串，签发失败时返回 null
     */
    public String createToken(String userId, Set<String> roles, int ttlMinutes) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId)
                    .claim(CLAIM_ROLES, String.join(",", roles))
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(ttlMinutes, ChronoUnit.MINUTES)))
                    .build();

            SignedJWT jwt = new SignedJWT(
                    new JWSHeader(JWSAlgorithm.HS256),
                    claims
            );
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            log.error("JWT 签发失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 签发默认有效期（5 分钟）的内部 JWT。
     *
     * @param userId 认证主体标识
     * @param roles  角色集合
     * @return 签发的 JWT 字符串
     */
    public String createToken(String userId, Set<String> roles) {
        return createToken(userId, roles, DEFAULT_TTL_MINUTES);
    }

    /**
     * 校验并解析内部 JWT。
     *
     * @param token JWT 字符串
     * @return 解析成功返回 GatewayAuthContext，签名无效/过期/格式错误时返回 null
     */
    public GatewayAuthContext validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                log.debug("JWT 签名校验失败");
                return null;
            }

            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration != null && expiration.before(new Date())) {
                log.debug("JWT 已过期: exp={}", expiration);
                return null;
            }

            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                return null;
            }

            String rolesStr = claims.getStringClaim(CLAIM_ROLES);
            Set<String> roles = rolesStr != null && !rolesStr.isBlank()
                    ? Set.of(rolesStr.split(","))
                    : Set.of();

            return new GatewayAuthContext(userId, roles);
        } catch (ParseException | JOSEException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }
}
