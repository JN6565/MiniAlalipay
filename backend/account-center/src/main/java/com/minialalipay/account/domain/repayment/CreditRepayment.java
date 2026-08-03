package com.minialalipay.account.domain.repayment;

import java.time.Instant;
import java.util.Objects;

/**
 * 信用还款事实记录。
 *
 * <p>绑定还款草稿和统一资金交易，避免重复确认产生多笔还款。
 * 唯一键 repaymentDraftId 和 transactionId 保证幂等。</p>
 */
public class CreditRepayment {

    private final String repaymentId;
    private final String repaymentDraftId;
    private final String transactionId;
    private final String creditAccountId;
    private final long amountFen;
    private CreditRepaymentStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * 提交还款时创建记录，初始状态为 PROCESSING。
     *
     * @param repaymentId 还款 ID（ULID）
     * @param repaymentDraftId 还款草稿 ID
     * @param transactionId 统一交易 ID
     * @param creditAccountId 信用账户 ID
     * @param amountFen 还款金额（分），必须在 1~5000000 范围内
     * @param now 创建时间
     */
    public CreditRepayment(
            String repaymentId, String repaymentDraftId, String transactionId,
            String creditAccountId, long amountFen, Instant now
    ) {
        this.repaymentId = Objects.requireNonNull(repaymentId, "还款 ID 不能为空");
        this.repaymentDraftId = Objects.requireNonNull(repaymentDraftId, "还款草稿 ID 不能为空");
        this.transactionId = Objects.requireNonNull(transactionId, "交易 ID 不能为空");
        this.creditAccountId = Objects.requireNonNull(creditAccountId, "信用账户 ID 不能为空");
        if (amountFen < 1 || amountFen > 5_000_000L) {
            throw new IllegalArgumentException("还款金额必须在 1~5000000 分范围内");
        }
        this.amountFen = amountFen;
        this.status = CreditRepaymentStatus.PROCESSING;
        this.createdAt = Objects.requireNonNull(now, "创建时间不能为空");
        this.updatedAt = now;
    }

    /**
     * 从持久化重建还款记录。
     */
    public CreditRepayment(
            String repaymentId, String repaymentDraftId, String transactionId,
            String creditAccountId, long amountFen, CreditRepaymentStatus status,
            Instant createdAt, Instant updatedAt
    ) {
        this.repaymentId = repaymentId;
        this.repaymentDraftId = repaymentDraftId;
        this.transactionId = transactionId;
        this.creditAccountId = creditAccountId;
        this.amountFen = amountFen;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 标记还款成功。终态，不可回退。
     *
     * @param now 当前时间
     */
    public void markSuccess(Instant now) {
        if (this.status != CreditRepaymentStatus.PROCESSING) {
            throw new IllegalStateException("仅 PROCESSING 状态可标记成功，当前状态: " + this.status);
        }
        this.status = CreditRepaymentStatus.SUCCESS;
        this.updatedAt = now;
    }

    /**
     * 标记还款取消。终态，不可回退。
     *
     * @param now 当前时间
     */
    public void markCancelled(Instant now) {
        if (this.status != CreditRepaymentStatus.PROCESSING) {
            throw new IllegalStateException("仅 PROCESSING 状态可标记取消，当前状态: " + this.status);
        }
        this.status = CreditRepaymentStatus.CANCELLED;
        this.updatedAt = now;
    }

    /** @return 还款 ID */
    public String getRepaymentId() { return repaymentId; }

    /** @return 还款草稿 ID */
    public String getRepaymentDraftId() { return repaymentDraftId; }

    /** @return 统一交易 ID */
    public String getTransactionId() { return transactionId; }

    /** @return 信用账户 ID */
    public String getCreditAccountId() { return creditAccountId; }

    /** @return 还款金额（分） */
    public long getAmountFen() { return amountFen; }

    /** @return 还款状态 */
    public CreditRepaymentStatus getStatus() { return status; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }

    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }
}
