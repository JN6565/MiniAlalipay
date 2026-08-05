package com.minialalipay.user.infrastructure.persistence.po;

import java.time.Instant;

/**
 * 支付密码证明持久化对象（Persistence Object）。
 *
 * <p>对应数据库表 {@code user_db.payment_proof}，用于 MyBatis 映射。</p>
 *
 * <p>与领域模型 {@link com.minialalipay.user.domain.credential.PaymentProof} 的区别：
 * <ul>
 *   <li>PO 使用基本类型和数据库兼容类型</li>
 *   <li>领域模型使用值对象和业务枚举</li>
 *   <li>PO 包含数据库特定字段（如字节数组的 tokenDigest）</li>
 * </ul>
 * </p>
 */
public class PaymentProofPO {

    /**
     * 支付证明 ID（CHAR(26)）。
     */
    private String proofId;

    /**
     * 原始证明令牌的 HMAC-SHA-256 摘要（BINARY(32)）。
     */
    private byte[] tokenDigest;

    /**
     * 证明所属用户 ID（CHAR(26)）。
     */
    private String userId;

    /**
     * 允许使用证明的确认用途（VARCHAR(32)）。
     */
    private String purpose;

    /**
     * 签发时的支付密码版本号（BIGINT UNSIGNED）。
     */
    private long payPasswordVersion;

    /**
     * 证明状态（VARCHAR(16)）。
     * <p>取值：ACTIVE、CONSUMED、REVOKED、EXPIRED</p>
     */
    private String status;

    /**
     * 证明有效期截止时间（DATETIME(3)）。
     */
    private Instant expiresAt;

    /**
     * 一次性消费完成时间（DATETIME(3)，可空）。
     */
    private Instant consumedAt;

    /**
     * 证明签发时间（DATETIME(3)）。
     */
    private Instant createdAt;

    public PaymentProofPO() {
    }

    // ==================== Getters & Setters ====================

    public String getProofId() { return proofId; }
    public void setProofId(String proofId) { this.proofId = proofId; }

    public byte[] getTokenDigest() { return tokenDigest; }
    public void setTokenDigest(byte[] tokenDigest) { this.tokenDigest = tokenDigest; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public long getPayPasswordVersion() { return payPasswordVersion; }
    public void setPayPasswordVersion(long payPasswordVersion) { this.payPasswordVersion = payPasswordVersion; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
