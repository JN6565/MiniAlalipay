package com.minialalipay.user.domain.credential;

import java.time.Instant;

/**
 * 支付密码证明实体。
 *
 * <p>保存支付密码验证成功后签发的短期一次性证明。
 * 业务库只保存其逻辑引用（proof_id），原始令牌不持久化。</p>
 *
 * <p>安全规则：
 * <ul>
 *   <li>只保存令牌的 HMAC-SHA-256 摘要（token_digest），不保存原始令牌</li>
 *   <li>签发和消费时必须校验当前支付密码版本（pay_password_version）</li>
 *   <li>一次确认最多消费一次证明</li>
 *   <li>修改支付密码时，该用户所有活动证明原子转为 REVOKED</li>
 *   <li>证明有效期通常为 2 分钟</li>
 * </ul>
 * </p>
 *
 * <p>状态流转：
 * <ul>
 *   <li>ACTIVE -> CONSUMED: 一次性消费完成</li>
 *   <li>ACTIVE -> REVOKED: 支付密码修改时废弃</li>
 *   <li>ACTIVE -> EXPIRED: 过期</li>
 * </ul>
 * </p>
 *
 * @see Credential 用户凭证
 * @see PaymentProofRepository 支付证明仓储
 */
public class PaymentProof {

    /**
     * 支付证明 ID（ULID 格式，26 位字符）。
     */
    private final String proofId;

    /**
     * 原始证明令牌的 HMAC-SHA-256 摘要（32 字节）。
     * <p>唯一约束，用于快速查找和防重放。</p>
     */
    private final byte[] tokenDigest;

    /**
     * 证明所属用户 ID。
     */
    private final String userId;

    /**
     * 允许使用证明的确认用途（如 TRANSFER、QR_PAY 等）。
     */
    private final String purpose;

    /**
     * 签发时的支付密码版本号。
     * <p>消费时必须校验当前版本号一致，否则证明无效。</p>
     */
    private final long payPasswordVersion;

    /**
     * 证明状态。
     */
    private ProofStatus status;

    /**
     * 证明有效期截止时间（UTC，毫秒精度）。
     */
    private final Instant expiresAt;

    /**
     * 一次性消费完成时间（UTC，毫秒精度，可空）。
     * <p>未消费时为 null。</p>
     */
    private Instant consumedAt;

    /**
     * 证明签发时间（UTC，毫秒精度）。
     */
    private final Instant createdAt;

    /**
     * 支付证明状态枚举。
     */
    public enum ProofStatus {
        /** 活动状态，可以消费 */
        ACTIVE,
        /** 已消费，一次性使用完成 */
        CONSUMED,
        /** 已废弃（支付密码修改时） */
        REVOKED,
        /** 已过期 */
        EXPIRED
    }

    /**
     * 创建新支付证明（签发时使用）。
     *
     * @param proofId            证明 ID
     * @param tokenDigest        令牌摘要（32 字节）
     * @param userId             用户 ID
     * @param purpose            确认用途
     * @param payPasswordVersion 支付密码版本号
     * @param expiresAt          过期时间
     */
    public PaymentProof(
            String proofId,
            byte[] tokenDigest,
            String userId,
            String purpose,
            long payPasswordVersion,
            Instant expiresAt
    ) {
        if (proofId == null || proofId.isBlank()) {
            throw new IllegalArgumentException("证明 ID 不能为空");
        }
        if (tokenDigest == null || tokenDigest.length != 32) {
            throw new IllegalArgumentException("令牌摘要必须为 32 字节");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("确认用途不能为空");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("过期时间不能为空");
        }

        this.proofId = proofId;
        this.tokenDigest = tokenDigest;
        this.userId = userId;
        this.purpose = purpose;
        this.payPasswordVersion = payPasswordVersion;
        this.status = ProofStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.consumedAt = null;
        this.createdAt = Instant.now();
    }

    /**
     * 重建支付证明对象（从数据库加载时使用）。
     */
    public PaymentProof(
            String proofId,
            byte[] tokenDigest,
            String userId,
            String purpose,
            long payPasswordVersion,
            ProofStatus status,
            Instant expiresAt,
            Instant consumedAt,
            Instant createdAt
    ) {
        this.proofId = proofId;
        this.tokenDigest = tokenDigest;
        this.userId = userId;
        this.purpose = purpose;
        this.payPasswordVersion = payPasswordVersion;
        this.status = status;
        this.expiresAt = expiresAt;
        this.consumedAt = consumedAt;
        this.createdAt = createdAt;
    }

    /**
     * 消费证明（一次性使用）。
     *
     * <p>只有 ACTIVE 状态的证明可以消费。
     * 消费后状态变为 CONSUMED，记录消费时间。</p>
     *
     * @throws IllegalStateException 如果证明不是 ACTIVE 状态
     */
    public void consume() {
        if (this.status != ProofStatus.ACTIVE) {
            throw new IllegalStateException("只有 ACTIVE 状态的证明可以消费，当前状态: " + this.status);
        }
        this.status = ProofStatus.CONSUMED;
        this.consumedAt = Instant.now();
    }

    /**
     * 废弃证明（支付密码修改时调用）。
     *
     * <p>只有 ACTIVE 状态的证明可以废弃。</p>
     *
     * @throws IllegalStateException 如果证明不是 ACTIVE 状态
     */
    public void revoke() {
        if (this.status != ProofStatus.ACTIVE) {
            throw new IllegalStateException("只有 ACTIVE 状态的证明可以废弃，当前状态: " + this.status);
        }
        this.status = ProofStatus.REVOKED;
    }

    /**
     * 检查证明是否已过期。
     *
     * @return 如果当前时间在过期时间之后则返回 true
     */
    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    /**
     * 检查证明是否可以消费。
     *
     * <p>必须同时满足：
     * <ul>
     *   <li>状态为 ACTIVE</li>
     *   <li>未过期</li>
     *   <li>支付密码版本号匹配</li>
     * </ul>
     * </p>
     *
     * @param currentPayPasswordVersion 当前支付密码版本号
     * @return 如果可以消费则返回 true
     */
    public boolean isConsumable(long currentPayPasswordVersion) {
        return this.status == ProofStatus.ACTIVE
                && !isExpired()
                && this.payPasswordVersion == currentPayPasswordVersion;
    }

    /**
     * 检查证明用途是否匹配。
     *
     * @param expectedPurpose 期望的用途
     * @return 如果用途匹配则返回 true
     */
    public boolean isPurposeMatching(String expectedPurpose) {
        return this.purpose.equals(expectedPurpose);
    }

    // ==================== Getters ====================

    public String getProofId() { return proofId; }
    public byte[] getTokenDigest() { return tokenDigest; }
    public String getUserId() { return userId; }
    public String getPurpose() { return purpose; }
    public long getPayPasswordVersion() { return payPasswordVersion; }
    public ProofStatus getStatus() { return status; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
