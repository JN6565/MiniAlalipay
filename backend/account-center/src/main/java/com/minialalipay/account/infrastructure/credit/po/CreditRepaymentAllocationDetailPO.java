package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;

/**
 * 信用还款分配明细持久化对象，对应 {@code ledger_db.credit_repayment_allocation_detail} 表。
 *
 * <p>该表记录还款分配的逐笔明细，指向具体消费及可选账单，
 * 父分配金额必须等于其明细合计。</p>
 */
public class CreditRepaymentAllocationDetailPO {

    /** 还款 ID，对应 CHAR(26) */
    private String repaymentId;

    /** 父分配序号，对应 INT */
    private Integer sequenceNo;

    /** 明细序号，对应 INT，从 1 开始 */
    private Integer detailNo;

    /** 消费明细 ID，对应 CHAR(26) */
    private String purchaseId;

    /** 账单 ID，对应 CHAR(26)，未出账消费分配时为 null */
    private String billId;

    /** 明细金额（分），对应 BIGINT UNSIGNED */
    private Long amountFen;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 无参构造器 */
    public CreditRepaymentAllocationDetailPO() {
    }

    /** 全参数构造器 */
    public CreditRepaymentAllocationDetailPO(String repaymentId, Integer sequenceNo, Integer detailNo,
                                             String purchaseId, String billId, Long amountFen,
                                             Instant createdAt) {
        this.repaymentId = repaymentId;
        this.sequenceNo = sequenceNo;
        this.detailNo = detailNo;
        this.purchaseId = purchaseId;
        this.billId = billId;
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

    public Integer getDetailNo() {
        return detailNo;
    }

    public void setDetailNo(Integer detailNo) {
        this.detailNo = detailNo;
    }

    public String getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(String purchaseId) {
        this.purchaseId = purchaseId;
    }

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
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
