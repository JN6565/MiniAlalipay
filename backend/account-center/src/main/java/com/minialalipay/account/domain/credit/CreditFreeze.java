package com.minialalipay.account.domain.credit;

import java.time.Instant;
import java.util.Objects;

/**
 * 信用支付 TCC 冻结记录。
 *
 * <p>每笔 CREDIT_PAY 交易在 Try 阶段创建一条冻结记录，保证 Try/Confirm/Cancel 幂等。
 * 唯一键 (transactionId, creditAccountId) 保证同一交易同一账户只有一条冻结记录。</p>
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link CreditFreezeStatus#FROZEN} → {@link CreditFreezeStatus#CONFIRMED}（Confirm 后）</li>
 *   <li>{@link CreditFreezeStatus#FROZEN} → {@link CreditFreezeStatus#RELEASED}（Cancel 后）</li>
 * </ul>
 * CONFIRMED 和 RELEASED 为终态，不可回退。</p>
 */
public class CreditFreeze {

    private final String creditFreezeId;
    private final String transactionId;
    private final String creditAccountId;
    private final long amountFen;
    private CreditFreezeStatus status;
    private final String branchXid;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * Try 阶段创建冻结记录。
     *
     * @param creditFreezeId 冻结记录 ID（ULID）
     * @param transactionId 统一交易 ID
     * @param creditAccountId 信用账户 ID
     * @param amountFen 冻结金额（分），必须为正且在 1~5000000 范围内
     * @param branchXid TCC 分支事务 ID
     * @param now 创建时间
     */
    public CreditFreeze(
            String creditFreezeId, String transactionId, String creditAccountId,
            long amountFen, String branchXid, Instant now
    ) {
        this.creditFreezeId = Objects.requireNonNull(creditFreezeId, "冻结记录 ID 不能为空");
        this.transactionId = Objects.requireNonNull(transactionId, "交易 ID 不能为空");
        this.creditAccountId = Objects.requireNonNull(creditAccountId, "信用账户 ID 不能为空");
        if (amountFen < 1 || amountFen > 5_000_000L) {
            throw new IllegalArgumentException("冻结金额必须在 1~5000000 分范围内");
        }
        this.amountFen = amountFen;
        this.status = CreditFreezeStatus.FROZEN;
        this.branchXid = Objects.requireNonNull(branchXid, "分支事务 ID 不能为空");
        this.version = 0L;
        this.createdAt = Objects.requireNonNull(now, "创建时间不能为空");
        this.updatedAt = now;
    }

    /**
     * 从持久化重建冻结记录。
     */
    public CreditFreeze(
            String creditFreezeId, String transactionId, String creditAccountId,
            long amountFen, CreditFreezeStatus status, String branchXid,
            long version, Instant createdAt, Instant updatedAt
    ) {
        this.creditFreezeId = creditFreezeId;
        this.transactionId = transactionId;
        this.creditAccountId = creditAccountId;
        this.amountFen = amountFen;
        this.status = status;
        this.branchXid = branchXid;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Confirm 阶段：标记冻结为已确认。终态，不可回退。
     *
     * @param now 当前时间
     */
    public void confirm(Instant now) {
        if (this.status != CreditFreezeStatus.FROZEN) {
            throw new IllegalStateException("仅 FROZEN 状态可确认，当前状态: " + this.status);
        }
        this.status = CreditFreezeStatus.CONFIRMED;
        this.updatedAt = now;
    }

    /**
     * Cancel 阶段：释放冻结。终态，不可回退。
     *
     * @param now 当前时间
     */
    public void release(Instant now) {
        if (this.status != CreditFreezeStatus.FROZEN) {
            throw new IllegalStateException("仅 FROZEN 状态可释放，当前状态: " + this.status);
        }
        this.status = CreditFreezeStatus.RELEASED;
        this.updatedAt = now;
    }

    /** @return 冻结记录 ID */
    public String getCreditFreezeId() { return creditFreezeId; }

    /** @return 统一交易 ID */
    public String getTransactionId() { return transactionId; }

    /** @return 信用账户 ID */
    public String getCreditAccountId() { return creditAccountId; }

    /** @return 冻结金额（分） */
    public long getAmountFen() { return amountFen; }

    /** @return 冻结状态 */
    public CreditFreezeStatus getStatus() { return status; }

    /** @return TCC 分支事务 ID */
    public String getBranchXid() { return branchXid; }

    /** @return 版本号 */
    public long getVersion() { return version; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }

    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }

    /** 更新版本号 */
    public void updateVersion(long version) { this.version = version; }
}
