package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 信用账单持久化对象，对应 {@code ledger_db.credit_bill} 表。
 *
 * <p>该表记录每个信用账户每个账单周期的账单汇总信息，包括账单总额、已还金额、
 * 冲正金额、未还金额及到期时间，是还款与逾期处理的主干数据。
 */
public class CreditBillPO {

    /** 账单 ID，对应 CHAR(26) */
    private String billId;

    /** 信用账户 ID，对应 CHAR(26) */
    private String creditAccountId;

    /** 账单周期，对应 VARCHAR */
    private String period;

    /** 账单日，对应 DATE */
    private LocalDate statementDate;

    /** 到期时间，对应 DATETIME(3) */
    private Instant dueAt;

    /** 账单总额（分），对应 BIGINT UNSIGNED */
    private Long totalFen;

    /** 已还金额（分），对应 BIGINT UNSIGNED */
    private Long paidFen;

    /** 冲正金额（分），对应 BIGINT UNSIGNED */
    private Long reversedFen;

    /** 未还金额（分），对应 BIGINT UNSIGNED */
    private Long outstandingFen;

    /** 账单状态，对应 CHAR */
    private String status;

    /** 乐观锁版本号，对应 BIGINT UNSIGNED */
    private Long version;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditBillPO() {
    }

    /** 全参数构造器 */
    public CreditBillPO(String billId, String creditAccountId, String period,
                        LocalDate statementDate, Instant dueAt, Long totalFen, Long paidFen,
                        Long reversedFen, Long outstandingFen, String status, Long version,
                        Instant createdAt, Instant updatedAt) {
        this.billId = billId;
        this.creditAccountId = creditAccountId;
        this.period = period;
        this.statementDate = statementDate;
        this.dueAt = dueAt;
        this.totalFen = totalFen;
        this.paidFen = paidFen;
        this.reversedFen = reversedFen;
        this.outstandingFen = outstandingFen;
        this.status = status;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public String getCreditAccountId() {
        return creditAccountId;
    }

    public void setCreditAccountId(String creditAccountId) {
        this.creditAccountId = creditAccountId;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public void setStatementDate(LocalDate statementDate) {
        this.statementDate = statementDate;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public void setDueAt(Instant dueAt) {
        this.dueAt = dueAt;
    }

    public Long getTotalFen() {
        return totalFen;
    }

    public void setTotalFen(Long totalFen) {
        this.totalFen = totalFen;
    }

    public Long getPaidFen() {
        return paidFen;
    }

    public void setPaidFen(Long paidFen) {
        this.paidFen = paidFen;
    }

    public Long getReversedFen() {
        return reversedFen;
    }

    public void setReversedFen(Long reversedFen) {
        this.reversedFen = reversedFen;
    }

    public Long getOutstandingFen() {
        return outstandingFen;
    }

    public void setOutstandingFen(Long outstandingFen) {
        this.outstandingFen = outstandingFen;
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
