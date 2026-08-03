package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;

/**
 * 信用还款草稿持久化对象，对应 {@code account_db.credit_repayment_draft} 表。
 *
 * <p>该表记录用户发起还款时生成的草稿，包含还款分配方案的快照与哈希校验值，
 * 在用户确认前保持待执行状态，超过有效期后自动失效。
 */
public class CreditRepaymentDraftPO {

    /** 还款草稿 ID，对应 CHAR(26) */
    private String repaymentDraftId;

    /** 用户 ID，对应 CHAR(26) */
    private String userId;

    /** 信用账户 ID，对应 CHAR(26) */
    private String creditAccountId;

    /** 付款账户 ID，对应 CHAR(26) */
    private String payerAccountId;

    /** 还款金额（分），对应 BIGINT UNSIGNED */
    private Long amountFen;

    /** 还款分配快照（JSON），对应 TEXT */
    private String allocationSnapshot;

    /** 分配哈希校验值，对应 BINARY/VARBINARY */
    private byte[] allocationHash;

    /** 草稿状态，对应 CHAR */
    private String status;

    /** 乐观锁版本号，对应 BIGINT UNSIGNED */
    private Long version;

    /** 过期时间，对应 DATETIME(3) */
    private Instant expiresAt;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditRepaymentDraftPO() {
    }

    /** 全参数构造器 */
    public CreditRepaymentDraftPO(String repaymentDraftId, String userId, String creditAccountId,
                                  String payerAccountId, Long amountFen, String allocationSnapshot,
                                  byte[] allocationHash, String status, Long version,
                                  Instant expiresAt, Instant createdAt, Instant updatedAt) {
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

    public String getRepaymentDraftId() {
        return repaymentDraftId;
    }

    public void setRepaymentDraftId(String repaymentDraftId) {
        this.repaymentDraftId = repaymentDraftId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCreditAccountId() {
        return creditAccountId;
    }

    public void setCreditAccountId(String creditAccountId) {
        this.creditAccountId = creditAccountId;
    }

    public String getPayerAccountId() {
        return payerAccountId;
    }

    public void setPayerAccountId(String payerAccountId) {
        this.payerAccountId = payerAccountId;
    }

    public Long getAmountFen() {
        return amountFen;
    }

    public void setAmountFen(Long amountFen) {
        this.amountFen = amountFen;
    }

    public String getAllocationSnapshot() {
        return allocationSnapshot;
    }

    public void setAllocationSnapshot(String allocationSnapshot) {
        this.allocationSnapshot = allocationSnapshot;
    }

    public byte[] getAllocationHash() {
        return allocationHash;
    }

    public void setAllocationHash(byte[] allocationHash) {
        this.allocationHash = allocationHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
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
