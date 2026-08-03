package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;

/**
 * 信用额度冻结持久化对象，对应 {@code account_db.credit_freeze} 表。
 *
 * <p>该表记录信用额度的冻结/解冻流水，用于在交易进行中临时占用信用额度，
 * 支持分支事务（Saga）的预留与确认/释放。
 */
public class CreditFreezePO {

    /** 信用冻结 ID，对应 CHAR(26) */
    private String creditFreezeId;

    /** 交易 ID，对应 CHAR(26) */
    private String transactionId;

    /** 信用账户 ID，对应 CHAR(26) */
    private String creditAccountId;

    /** 冻结金额（分），对应 BIGINT UNSIGNED */
    private Long amountFen;

    /** 冻结状态，对应 CHAR */
    private String status;

    /** 分支事务 XID，对应 VARCHAR */
    private String branchXid;

    /** 乐观锁版本号，对应 BIGINT UNSIGNED */
    private Long version;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditFreezePO() {
    }

    /** 全参数构造器 */
    public CreditFreezePO(String creditFreezeId, String transactionId, String creditAccountId,
                          Long amountFen, String status, String branchXid, Long version,
                          Instant createdAt, Instant updatedAt) {
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

    public String getCreditFreezeId() {
        return creditFreezeId;
    }

    public void setCreditFreezeId(String creditFreezeId) {
        this.creditFreezeId = creditFreezeId;
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

    public String getBranchXid() {
        return branchXid;
    }

    public void setBranchXid(String branchXid) {
        this.branchXid = branchXid;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
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
