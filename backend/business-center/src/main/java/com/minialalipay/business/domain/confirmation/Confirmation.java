package com.minialalipay.business.domain.confirmation;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * 一次性资金确认上下文。持久化对象只持有令牌 SHA-256 摘要，不持有原始令牌。
 */
public final class Confirmation {
    private static final Duration VALIDITY = Duration.ofMinutes(2);
    private final String confirmationId;
    private final byte[] tokenDigest;
    private final SubjectType subjectType;
    private final String subjectId;
    private final byte[] subjectHash;
    private final String payerUserId;
    private final String paymentProofId;
    private final long payPasswordVersion;
    private ConfirmationStatus status;
    private final Instant expiresAt;
    private Instant consumedAt;
    private final Instant createdAt;

    /** 签发两分钟有效的确认上下文。 */
    public static Confirmation issue(String confirmationId, byte[] tokenDigest, SubjectType subjectType,
                                     String subjectId, byte[] subjectHash, String payerUserId,
                                     String paymentProofId, long payPasswordVersion, Instant now) {
        return new Confirmation(confirmationId, tokenDigest, subjectType, subjectId, subjectHash,
                payerUserId, paymentProofId, payPasswordVersion, ConfirmationStatus.ACTIVE,
                now.plus(VALIDITY), null, now);
    }

    /** 从持久化事实重建确认上下文。 */
    public Confirmation(String confirmationId, byte[] tokenDigest, SubjectType subjectType, String subjectId,
                        byte[] subjectHash, String payerUserId, String paymentProofId, long payPasswordVersion,
                        ConfirmationStatus status, Instant expiresAt, Instant consumedAt, Instant createdAt) {
        this.confirmationId = required(confirmationId);
        this.tokenDigest = digest(tokenDigest);
        this.subjectType = Objects.requireNonNull(subjectType);
        this.subjectId = required(subjectId);
        this.subjectHash = digest(subjectHash);
        this.payerUserId = required(payerUserId);
        this.paymentProofId = required(paymentProofId);
        if (payPasswordVersion < 0) throw new IllegalArgumentException("支付密码版本不得为负");
        this.payPasswordVersion = payPasswordVersion;
        this.status = Objects.requireNonNull(status);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.consumedAt = consumedAt;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** 消费一次有效确认；重复、撤销或过期确认均拒绝。 */
    public void consume(Instant now) {
        if (status != ConfirmationStatus.ACTIVE) throw new IllegalStateException("确认令牌已失效");
        if (!now.isBefore(expiresAt)) {
            status = ConfirmationStatus.EXPIRED;
            throw new IllegalStateException("确认令牌已过期");
        }
        status = ConfirmationStatus.CONSUMED;
        consumedAt = now;
    }

    /** 撤销尚未消费的确认上下文。 */
    public void revoke() { if (status == ConfirmationStatus.ACTIVE) status = ConfirmationStatus.REVOKED; }
    public boolean matchesTokenDigest(byte[] digest) { return Arrays.equals(tokenDigest, digest); }

    private static byte[] digest(byte[] value) {
        if (value == null || value.length != 32) throw new IllegalArgumentException("摘要必须为 32 字节");
        return value.clone();
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("确认字段不能为空");
        return value;
    }
    public String getConfirmationId() { return confirmationId; }
    public byte[] getTokenDigest() { return tokenDigest.clone(); }
    public SubjectType getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public byte[] getSubjectHash() { return subjectHash.clone(); }
    public String getPayerUserId() { return payerUserId; }
    public String getPaymentProofId() { return paymentProofId; }
    public long getPayPasswordVersion() { return payPasswordVersion; }
    public ConfirmationStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
