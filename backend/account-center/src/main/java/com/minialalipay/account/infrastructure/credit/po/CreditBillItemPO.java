package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;

/**
 * 信用账单明细持久化对象，对应 {@code ledger_db.credit_bill_item} 表。
 *
 * <p>该表记录账单中每一笔消费明细的入账与还款分配情况，
 * 将信用消费（credit_purchase）与账单（credit_bill）关联起来。
 */
public class CreditBillItemPO {

    /** 账单 ID，对应 CHAR(26) */
    private String billId;

    /** 消费 ID，对应 CHAR(26) */
    private String purchaseId;

    /** 明细金额（分），对应 BIGINT UNSIGNED */
    private Long amountFen;

    /** 已分配还款金额（分），对应 BIGINT UNSIGNED */
    private Long allocatedPaidFen;

    /** 冲正金额（分），对应 BIGINT UNSIGNED */
    private Long reversedFen;

    /** 明细状态，对应 CHAR */
    private String status;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditBillItemPO() {
    }

    /** 全参数构造器 */
    public CreditBillItemPO(String billId, String purchaseId, Long amountFen,
                            Long allocatedPaidFen, Long reversedFen, String status,
                            Instant createdAt, Instant updatedAt) {
        this.billId = billId;
        this.purchaseId = purchaseId;
        this.amountFen = amountFen;
        this.allocatedPaidFen = allocatedPaidFen;
        this.reversedFen = reversedFen;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getBillId() {
        return billId;
    }

    public void setBillId(String billId) {
        this.billId = billId;
    }

    public String getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(String purchaseId) {
        this.purchaseId = purchaseId;
    }

    public Long getAmountFen() {
        return amountFen;
    }

    public void setAmountFen(Long amountFen) {
        this.amountFen = amountFen;
    }

    public Long getAllocatedPaidFen() {
        return allocatedPaidFen;
    }

    public void setAllocatedPaidFen(Long allocatedPaidFen) {
        this.allocatedPaidFen = allocatedPaidFen;
    }

    public Long getReversedFen() {
        return reversedFen;
    }

    public void setReversedFen(Long reversedFen) {
        this.reversedFen = reversedFen;
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
