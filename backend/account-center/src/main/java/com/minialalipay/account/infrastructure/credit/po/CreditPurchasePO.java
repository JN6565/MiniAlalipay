package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;

/**
 * 信用消费持久化对象，对应 {@code ledger_db.credit_purchase} 表。
 *
 * <p>该表记录每一笔信用消费交易的明细，包括消费金额、已还金额、已退款金额及未偿金额，
 * 是账单明细项和还款分配的核心数据来源。
 */
public class CreditPurchasePO {

    /** 消费 ID，对应 CHAR(26) */
    private String purchaseId;

    /** 信用交易 ID，对应 CHAR(26) */
    private String creditTransactionId;

    /** 信用账户 ID，对应 CHAR(26) */
    private String creditAccountId;

    /** 二维码订单 ID，对应 CHAR(26) */
    private String qrOrderId;

    /** 商户账户 ID，对应 CHAR(26) */
    private String merchantAccountId;

    /** 消费金额（分），对应 BIGINT UNSIGNED */
    private Long amountFen;

    /** 已还金额（分），对应 BIGINT UNSIGNED */
    private Long repaidFen;

    /** 已退款金额（分），对应 BIGINT UNSIGNED */
    private Long refundedFen;

    /** 未偿金额（分），对应 BIGINT UNSIGNED */
    private Long outstandingFen;

    /** 退款交易 ID，对应 CHAR(26) */
    private String refundTransactionId;

    /** 出账状态，对应 CHAR */
    private String billingStatus;

    /** 乐观锁版本号，对应 BIGINT UNSIGNED */
    private Long version;

    /** 发生时间，对应 DATETIME(3) */
    private Instant occurredAt;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditPurchasePO() {
    }

    /** 全参数构造器 */
    public CreditPurchasePO(String purchaseId, String creditTransactionId, String creditAccountId,
                            String qrOrderId, String merchantAccountId, Long amountFen,
                            Long repaidFen, Long refundedFen, Long outstandingFen,
                            String refundTransactionId, String billingStatus, Long version,
                            Instant occurredAt, Instant updatedAt) {
        this.purchaseId = purchaseId;
        this.creditTransactionId = creditTransactionId;
        this.creditAccountId = creditAccountId;
        this.qrOrderId = qrOrderId;
        this.merchantAccountId = merchantAccountId;
        this.amountFen = amountFen;
        this.repaidFen = repaidFen;
        this.refundedFen = refundedFen;
        this.outstandingFen = outstandingFen;
        this.refundTransactionId = refundTransactionId;
        this.billingStatus = billingStatus;
        this.version = version;
        this.occurredAt = occurredAt;
        this.updatedAt = updatedAt;
    }

    public String getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(String purchaseId) {
        this.purchaseId = purchaseId;
    }

    public String getCreditTransactionId() {
        return creditTransactionId;
    }

    public void setCreditTransactionId(String creditTransactionId) {
        this.creditTransactionId = creditTransactionId;
    }

    public String getCreditAccountId() {
        return creditAccountId;
    }

    public void setCreditAccountId(String creditAccountId) {
        this.creditAccountId = creditAccountId;
    }

    public String getQrOrderId() {
        return qrOrderId;
    }

    public void setQrOrderId(String qrOrderId) {
        this.qrOrderId = qrOrderId;
    }

    public String getMerchantAccountId() {
        return merchantAccountId;
    }

    public void setMerchantAccountId(String merchantAccountId) {
        this.merchantAccountId = merchantAccountId;
    }

    public Long getAmountFen() {
        return amountFen;
    }

    public void setAmountFen(Long amountFen) {
        this.amountFen = amountFen;
    }

    public Long getRepaidFen() {
        return repaidFen;
    }

    public void setRepaidFen(Long repaidFen) {
        this.repaidFen = repaidFen;
    }

    public Long getRefundedFen() {
        return refundedFen;
    }

    public void setRefundedFen(Long refundedFen) {
        this.refundedFen = refundedFen;
    }

    public Long getOutstandingFen() {
        return outstandingFen;
    }

    public void setOutstandingFen(Long outstandingFen) {
        this.outstandingFen = outstandingFen;
    }

    public String getRefundTransactionId() {
        return refundTransactionId;
    }

    public void setRefundTransactionId(String refundTransactionId) {
        this.refundTransactionId = refundTransactionId;
    }

    public String getBillingStatus() {
        return billingStatus;
    }

    public void setBillingStatus(String billingStatus) {
        this.billingStatus = billingStatus;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
