package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;

/**
 * 信用还款分配持久化对象，对应 {@code ledger_db.credit_repayment_allocation} 表。
 *
 * <p>该表记录还款分配计划的主干信息，按固定优先级顺序（逾期账单 → 已出账账单 → 未出账消费）
 * 固化分配金额，Try 阶段写入后 Confirm 阶段不得重新计算。</p>
 */
public class CreditRepaymentAllocationPO {

    /** 还款 ID，对应 CHAR(26) */
    private String repaymentId;

    /** 分配序号，对应 INT，从 1 开始 */
    private Integer sequenceNo;

    /** 分配目标类型，对应 CHAR */
    private String targetType;

    /** 目标 ID，对应 CHAR(26) */
    private String targetId;

    /** 分配金额（分），对应 BIGINT UNSIGNED */
    private Long amountFen;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 无参构造器 */
    public CreditRepaymentAllocationPO() {
    }

    /** 全参数构造器 */
    public CreditRepaymentAllocationPO(String repaymentId, Integer sequenceNo, String targetType,
                                       String targetId, Long amountFen, Instant createdAt) {
        this.repaymentId = repaymentId;
        this.sequenceNo = sequenceNo;
        this.targetType = targetType;
        this.targetId = targetId;
        this.amountFen = amountFen;
        this.createdAt = createdAt;
    }

    public String getRepaymentId() {
        return repaymentId;
    }

    public void setRepaymentId(String repaymentId) {
        this.repaymentId = repaymentId;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public Long getAmountFen() {
        return amountFen;
    }

    public void setAmountFen(Long amountFen) {
        this.amountFen = amountFen;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
