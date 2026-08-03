package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;

/**
 * 信用应收持久化对象，对应 {@code ledger_db.credit_receivable} 表。
 *
 * <p>该表以信用账户为单位汇总应收账款，区分未出账、已出账及逾期金额，
 * 作为账单生成与逾期处理的汇总数据源。
 */
public class CreditReceivablePO {

    /** 信用账户 ID，对应 CHAR(26) */
    private String creditAccountId;

    /** 未出账金额（分），对应 BIGINT UNSIGNED */
    private Long unbilledFen;

    /** 已出账金额（分），对应 BIGINT UNSIGNED */
    private Long billedFen;

    /** 逾期金额（分），对应 BIGINT UNSIGNED */
    private Long overdueFen;

    /** 乐观锁版本号，对应 BIGINT UNSIGNED */
    private Long version;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditReceivablePO() {
    }

    /** 全参数构造器 */
    public CreditReceivablePO(String creditAccountId, Long unbilledFen, Long billedFen,
                              Long overdueFen, Long version, Instant updatedAt) {
        this.creditAccountId = creditAccountId;
        this.unbilledFen = unbilledFen;
        this.billedFen = billedFen;
        this.overdueFen = overdueFen;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public String getCreditAccountId() {
        return creditAccountId;
    }

    public void setCreditAccountId(String creditAccountId) {
        this.creditAccountId = creditAccountId;
    }

    public Long getUnbilledFen() {
        return unbilledFen;
    }

    public void setUnbilledFen(Long unbilledFen) {
        this.unbilledFen = unbilledFen;
    }

    public Long getBilledFen() {
        return billedFen;
    }

    public void setBilledFen(Long billedFen) {
        this.billedFen = billedFen;
    }

    public Long getOverdueFen() {
        return overdueFen;
    }

    public void setOverdueFen(Long overdueFen) {
        this.overdueFen = overdueFen;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
