package com.minialalipay.account.domain.repayment;

import java.time.Instant;
import java.util.Objects;

/**
 * 信用还款草稿。还款确认前的金额与分配快照，绑定一次性确认令牌。
 *
 * <p>关键约束：
 * <ul>
 *   <li>金额不得超过虚拟可用余额或信用应收</li>
 *   <li>allocationHash 绑定分配预览，确认令牌必须与同一快照匹配</li>
 *   <li>草稿有过期时间</li>
 * </ul>
 * </p>
 */
public class CreditRepaymentDraft {

    private final String repaymentDraftId;
    private final String userId;
    private final String creditAccountId;
    private final String payerAccountId;
    private final long amountFen;
    private final String allocationSnapshot;
    private final byte[] allocationHash;
    private CreditRepaymentDraftStatus status;
    private long version;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * 创建还款草稿。
     *
     * @param repaymentDraftId 草稿 ID（ULID）
     * @param userId 用户 ID
     * @param creditAccountId 信用账户 ID
     * @param payerAccountId 付款方账户 ID
     * @param amountFen 还款金额（分），必须在 1~5000000 范围内
     * @param allocationSnapshot 分配快照 JSON
     * @param allocationHash 分配哈希，绑定快照
     * @param expiresAt 过期时间
     * @param now 创建时间
     */
    public CreditRepaymentDraft(
            String repaymentDraftId, String userId, String creditAccountId,
            String payerAccountId, long amountFen,
            String allocationSnapshot, byte[] allocationHash,
            Instant expiresAt, Instant now
    ) {
        this.repaymentDraftId = Objects.requireNonNull(repaymentDraftId, "草稿 ID 不能为空");
        this.userId = Objects.requireNonNull(userId, "用户 ID 不能为空");
        this.creditAccountId = Objects.requireNonNull(creditAccountId, "信用账户 ID 不能为空");
        this.payerAccountId = Objects.requireNonNull(payerAccountId, "付款方账户 ID 不能为空");
        if (amountFen < 1 || amountFen > 5_000_000L) {
            throw new IllegalArgumentException("还款金额必须在 1~5000000 分范围内");
        }
        this.amountFen = amountFen;
        this.allocationSnapshot = Objects.requireNonNull(allocationSnapshot, "分配快照不能为空");
        this.allocationHash = Objects.requireNonNull(allocationHash, "分配哈希不能为空");
        this.status = CreditRepaymentDraftStatus.DRAFT;
        this.version = 0L;
        this.expiresAt = Objects.requireNonNull(expiresAt, "过期时间不能为空");
        this.createdAt = Objects.requireNonNull(now, "创建时间不能为空");
        this.updatedAt = now;
    }

    /**
     * 从持久化重建还款草稿。
     */
    public CreditRepaymentDraft(
            String repaymentDraftId, String userId, String creditAccountId,
            String payerAccountId, long amountFen,
            String allocationSnapshot, byte[] allocationHash,
            CreditRepaymentDraftStatus status, long version,
            Instant expiresAt, Instant createdAt, Instant updatedAt
    ) {
        this.repaymentDraftId = repaymentDraftId;
        this.userId = userId;
        this.creditAccountId = creditAccountId;
        this.payerAccountId = payerAccountId;
        this.amountFen = amountFen;
        this.allocationSnapshot = allocationSnapshot;
        this.allocationHash = allocationHash;
        this.status = status;
        this.version = version;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 标记草稿为已确认。
     *
     * @param now 当前时间
     */
    public void confirm(Instant now) {
        if (this.status != CreditRepaymentDraftStatus.DRAFT) {
            throw new IllegalStateException("仅 DRAFT 状态可确认，当前状态: " + this.status);
        }
        if (isExpired(now)) {
            throw new IllegalStateException("草稿已过期，不可确认");
        }
        this.status = CreditRepaymentDraftStatus.CONFIRMED;
        this.updatedAt = now;
    }

    /**
     * 标记草稿已被还款交易消费。终态。
     *
     * @param now 当前时间
     */
    public void consume(Instant now) {
        if (this.status != CreditRepaymentDraftStatus.CONFIRMED) {
            throw new IllegalStateException("仅 CONFIRMED 状态可消费，当前状态: " + this.status);
        }
        this.status = CreditRepaymentDraftStatus.CONSUMED;
        this.updatedAt = now;
    }

    /**
     * 标记草稿过期。
     *
     * @param now 当前时间
     */
    public void expire(Instant now) {
        if (this.status == CreditRepaymentDraftStatus.CONSUMED) {
            return;
        }
        this.status = CreditRepaymentDraftStatus.EXPIRED;
        this.updatedAt = now;
    }

    /** @return 草稿是否已过期 */
    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** @return 草稿 ID */
    public String getRepaymentDraftId() { return repaymentDraftId; }

    /** @return 用户 ID */
    public String getUserId() { return userId; }

    /** @return 信用账户 ID */
    public String getCreditAccountId() { return creditAccountId; }

    /** @return 付款方账户 ID */
    public String getPayerAccountId() { return payerAccountId; }

    /** @return 还款金额（分） */
    public long getAmountFen() { return amountFen; }

    /** @return 分配快照 JSON */
    public String getAllocationSnapshot() { return allocationSnapshot; }

    /** @return 分配哈希 */
    public byte[] getAllocationHash() { return allocationHash; }

    /** @return 草稿状态 */
    public CreditRepaymentDraftStatus getStatus() { return status; }

    /** @return 版本号 */
    public long getVersion() { return version; }

    /** @return 过期时间 */
    public Instant getExpiresAt() { return expiresAt; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }

    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }

    /** 更新版本号 */
    public void updateVersion(long version) { this.version = version; }
}
