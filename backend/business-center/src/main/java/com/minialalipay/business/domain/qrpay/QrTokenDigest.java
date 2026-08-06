package com.minialalipay.business.domain.qrpay;

import java.util.Locale;
import java.util.Objects;

/**
 * 动态二维码原始令牌的 SHA-256 摘要。
 *
 * <p>领域对象只接受固定长度摘要，避免原始令牌进入订单、日志或持久化模型。令牌校验和摘要计算
 * 位于受保护的接口边界，本对象不保存原始令牌。</p>
 */
public record QrTokenDigest(String value) {
    private static final int SHA_256_HEX_LENGTH = 64;

    /**
     * 按十六进制形式创建摘要。
     *
     * @param value SHA-256 摘要，必须是 64 位十六进制字符
     */
    public QrTokenDigest {
        Objects.requireNonNull(value, "二维码令牌摘要不能为空");
        if (!value.matches("[0-9a-fA-F]{" + SHA_256_HEX_LENGTH + "}")) {
            throw new IllegalArgumentException("二维码令牌摘要格式不正确");
        }
        value = value.toLowerCase(Locale.ROOT);
    }

    /**
     * 将十六进制摘要转换为领域值对象。
     *
     * @param value SHA-256 摘要
     * @return 令牌摘要
     */
    public static QrTokenDigest fromHex(String value) {
        return new QrTokenDigest(value);
    }
}
