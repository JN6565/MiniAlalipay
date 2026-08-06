package com.minialalipay.business.infrastructure.security;

import com.minialalipay.business.application.port.SecurityMaterialPort;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.ByteBuffer;

/** 生成不可预测业务 ID、原始短期令牌及稳定 SHA-256 请求摘要。 */
@Component
public class SecureMaterialService implements SecurityMaterialPort {
    private static final char[] BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();

    /** 生成 26 位不透明业务 ID。 */
    @Override public String newId() {
        char[] value = new char[26];
        for (int i = 0; i < value.length; i++) value[i] = BASE32[random.nextInt(BASE32.length)];
        return new String(value);
    }
    /** 生成 32 位十六进制链路 ID。 */
    @Override public String newTraceId() {
        byte[] bytes = new byte[16]; random.nextBytes(bytes); return java.util.HexFormat.of().formatHex(bytes);
    }
    /** 生成只在可信响应边界返回的一次性确认原始令牌。 */
    @Override public String newConfirmationToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return "cfm_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    /** 生成仅用于二维码 H5 引导与短期交换的随机令牌。 */
    @Override public String newQrToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return "qr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    /** 生成个人收款公开入口使用的随机令牌。 */
    @Override public String newCollectionToken() {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        return "pc_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    /** 对规范化 UTF-8 文本计算 SHA-256 摘要。 */
    @Override public byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("运行环境缺少 SHA-256", impossible); }
    }

    /** 从稳定业务键派生 26 位技术资源 ID，供恢复重试复用同一分支参数。 */
    @Override public String stableId(String value) {
        return java.util.HexFormat.of().formatHex(digest(value)).substring(0, 26).toUpperCase();
    }
    /** 从稳定业务键派生正数分录 ID，重复协调不会改变分录唯一键。 */
    @Override public long stablePositiveLong(String value) {
        return ByteBuffer.wrap(digest(value)).getLong() & Long.MAX_VALUE;
    }
}
