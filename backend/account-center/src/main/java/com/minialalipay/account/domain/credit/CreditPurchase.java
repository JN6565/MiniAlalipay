package com.minialalipay.account.domain.credit;

import java.time.Instant;
import java.util.Objects;

/**
 * 信用消费明细。
 *
 * <p>每笔成功 CREDIT_PAY 生成一条不可重复的消费明细。
 * 唯一键 creditTransactionId 保证一笔支付不能重复进入账单。</p>
 *
 * <p>出账状态流转：
 * <ul>
 *   <li>{@link CreditPurchaseBillingStatus#UNBILLED}：初始状态</li>
 *   <li>{@link CreditPurchaseBillingStatus#BILLED}：月度出账后</li>
 *   <li>{@link CreditPurchaseBillingStatus#REPAID}：全额还清后（终态）</li>
 *   <li>{@link CreditPurchaseBillingStatus#REVERSED}：退款冲正后（终态）</li>
 * </ul>
 * </p>
 */
public class CreditPurchase {

    private final String purchaseId;
    private final String creditTransactionId;
    private final String creditAccountId;
    private final String qrOrderId;
    private final String merchantAccountId;
    private final long amountFen;
    private long repaidFen;
    private long refundedFen;
    private String refundTransactionId;
    private CreditPurchaseBillingStatus billingStatus;
    private long version;
    private final Instant occurredAt;
    private Instant updatedAt;

    /**
     * CREDIT_PAY Confirm 成功后创建消费明细。
     *
     * @param purchaseId 消费明细 ID（ULID）
     * @param creditTransactionId 信用支付交易 ID
     * @param creditAccountId 信用账户 ID
     * @param qrOrderId 扫码订单 ID
     * @param merchantAccountId 收款方账户 ID
     * @param amountFen 消费金额（分），必须在 1~5000000 范围内
     * @param now 发生时间
     */
    public CreditPurchase(
            String purchaseId, String creditTransactionId, String creditAccountId,
            String qrOrderId, String merchantAccountId, long amountFen, Instant now
    ) {
        this.purchaseId = Objects.requireNonNull(purchaseId, "消费明细 ID 不能为空");
        this.creditTransactionId = Objects.requireNonNull(creditTransactionId, "交易 ID 不能为空");
        this.creditAccountId = Objects.requireNonNull(creditAccountId, "信用账户 ID 不能为空");
        this.qrOrderId = Objects.requireNonNull(qrOrderId, "扫码订单 ID 不能为空");
        this.merchantAccountId = Objects.requireNonNull(merchantAccountId, "收款方账户 ID 不能为空");
        if (amountFen < 1 || amountFen > 5_000_000L) {
            throw new IllegalArgumentException("消费金额必须在 1~5000000 分范围内");
        }
        this.amountFen = amountFen;
        this.repaidFen = 0L;
        this.refundedFen = 0L;
        this.refundTransactionId = null;
        this.billingStatus = CreditPurchaseBillingStatus.UNBILLED;
        this.version = 0L;
        this.occurredAt = Objects.requireNonNull(now, "发生时间不能为空");
        this.updatedAt = now;
    }

    /**
     * 从持久化重建消费明细。
     */
    public CreditPurchase(
            String purchaseId, String creditTransactionId, String creditAccountId,
            String qrOrderId, String merchantAccountId, long amountFen,
            long repaidFen, long refundedFen, String refundTransactionId,
            CreditPurchaseBillingStatus billingStatus, long version,
            Instant occurredAt, Instant updatedAt
    ) {
        this.purchaseId = purchaseId;
        this.creditTransactionId = creditTransactionId;
        this.creditAccountId = creditAccountId;
        this.qrOrderId = qrOrderId;
        this.merchantAccountId = merchantAccountId;
        this.amountFen = amountFen;
        this.repaidFen = repaidFen;
        this.refundedFen = refundedFen;
        this.refundTransactionId = refundTransactionId;
        this.billingStatus = billingStatus;
        this.version = version;
        this.occurredAt = occurredAt;
        this.updatedAt = updatedAt;
        validateInvariants();
    }

    /**
     * 月度出账：标记为已出账。
     *
     * @param now 当前时间
     */
    public void markBilled(Instant now) {
        if (this.billingStatus != CreditPurchaseBillingStatus.UNBILLED) {
            throw new IllegalStateException("仅未出账消费可出账，当前状态: " + this.billingStatus);
        }
        this.billingStatus = CreditPurchaseBillingStatus.BILLED;
        this.updatedAt = now;
    }

    /**
     * 应用还款分配金额。当已还金额等于消费金额时标记为已还清。
     *
     * @param allocatedFen 分配到本笔消费的还款金额（分），必须为正
     * @param now 当前时间
     */
    public void applyRepayment(long allocatedFen, Instant now) {
        if (allocatedFen <= 0) {
            throw new IllegalArgumentException("分配金额必须为正");
        }
        if (this.repaidFen + allocatedFen > this.amountFen - this.refundedFen) {
            throw new IllegalStateException("分配金额超过消费未还余额");
        }
        this.repaidFen += allocatedFen;
        if (this.repaidFen + this.refundedFen >= this.amountFen) {
            this.billingStatus = CreditPurchaseBillingStatus.REPAID;
        }
        this.updatedAt = now;
        validateInvariants();
    }

    /** @return 未还余额（分）= 消费金额 - 已还 - 已退款 */
    public long getOutstandingFen() {
        return amountFen - repaidFen - refundedFen;
    }

    /** @return 消费明细 ID */
    public String getPurchaseId() { return purchaseId; }

    /** @return 信用支付交易 ID */
    public String getCreditTransactionId() { return creditTransactionId; }

    /** @return 信用账户 ID */
    public String getCreditAccountId() { return creditAccountId; }

    /** @return 扫码订单 ID */
    public String getQrOrderId() { return qrOrderId; }

    /** @return 收款方账户 ID */
    public String getMerchantAccountId() { return merchantAccountId; }

    /** @return 消费金额（分） */
    public long getAmountFen() { return amountFen; }

    /** @return 已还金额（分） */
    public long getRepaidFen() { return repaidFen; }

    /** @return 已退款金额（分） */
    public long getRefundedFen() { return refundedFen; }

    /** @return 退款交易 ID */
    public String getRefundTransactionId() { return refundTransactionId; }

    /** @return 出账状态 */
    public CreditPurchaseBillingStatus getBillingStatus() { return billingStatus; }

    /** @return 版本号 */
    public long getVersion() { return version; }

    /** @return 发生时间 */
    public Instant getOccurredAt() { return occurredAt; }

    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }

    /** 更新版本号 */
    public void updateVersion(long version) { this.version = version; }

    private void validateInvariants() {
        if (repaidFen + refundedFen > amountFen) {
            throw new IllegalStateException("已还与已退款之和不得超过消费金额");
        }
    }
}
