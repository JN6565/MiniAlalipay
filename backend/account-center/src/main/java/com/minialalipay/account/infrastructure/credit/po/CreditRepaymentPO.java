package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;

/**
 * 信用还款持久化对象，对应 {@code ledger_db.credit_repayment} 表。
 *
 * <p>该表记录每笔信用还款的事实记录，绑定还款草稿与统一资金交易，
 * 通过 repaymentDraftId 和 transactionId 双重唯一键保证还款幂等。</p>
 */
public class CreditRepaymentPO {

    /** 还款 ID，对应 CHAR(26) */
    private String repaymentId;

    /** 还款草稿 ID，对应 CHAR(26) */
    private String repaymentDraftId;

    /** 统一交易 ID，对应 CHAR(26) */
    private String transactionId;

    /** 信用账户 ID，对应 CHAR(26) */
    private String creditAccountId;

    /** 还款金额（分），对应 BIGINT UNSIGNED */
    private Long amountFen;

    /** 还款状态，对应 CHAR */
    private String status;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditRepaymentPO() {
    }

    /** 全参数构造器 */
    public CreditRepaymentPO(String repaymentId, String repaymentDraftId, String transactionId,
                             String creditAccountId, Long amountFen, String status,
                             Instant createdAt, Instant updatedAt) {
        this.repaymentId = repaymentId;
        this.repaymentDraftId = repaymentDraftId;
        this.transactionId = transactionId;
        this.creditAccountId = creditAccountId;
        this.amountFen = amountFen;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getRepaymentId() {
        return repaymentId;
    }

    public void setRepaymentId(String repaymentId) {
        this.repaymentId = repaymentId;
    }

    public String getRepaymentDraftId() {
        return repaymentDraftId;
    }

    public void setRepaymentDraftId(String repaymentDraftId) {
        this.repaymentDraftId = repaymentDraftId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getCreditAccountId() {
        return creditAccountId;
    }

    public void setCreditAccountId(String creditAccountId) {
        this.creditAccountId = creditAccountId;
    }

    public Long getAmountFen() {
        return amountFen;
    }

    public void setAmountFen(Long amountFen) {
        this.amountFen = amountFen;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
