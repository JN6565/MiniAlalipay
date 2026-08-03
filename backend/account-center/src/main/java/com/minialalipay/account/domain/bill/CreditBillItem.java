package com.minialalipay.account.domain.bill;

import java.time.Instant;
import java.util.Objects;

/**
 * 账单与信用消费的不可变关联记录。
 *
 * <p>每笔消费最多进入一个账期，purchaseId 唯一约束保证不可重复。
 * 记录该笔消费在账单中的金额及已分配还款金额。</p>
 */
public class CreditBillItem {

    private final String billId;
    private final String purchaseId;
    private final long amountFen;
    private long allocatedPaidFen;
    private long reversedFen;
    private CreditBillItemStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * 出账时创建账单明细。
     *
     * @param billId 账单 ID
     * @param purchaseId 消费明细 ID
     * @param amountFen 消费金额（分），必须为正
     * @param now 创建时间
     */
    public CreditBillItem(String billId, String purchaseId, long amountFen, Instant now) {
        this.billId = Objects.requireNonNull(billId, "账单 ID 不能为空");
        this.purchaseId = Objects.requireNonNull(purchaseId, "消费明细 ID 不能为空");
        if (amountFen <= 0) {
            throw new IllegalArgumentException("明细金额必须为正");
        }
        this.amountFen = amountFen;
        this.allocatedPaidFen = 0L;
        this.reversedFen = 0L;
        this.status = CreditBillItemStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(now, "创建时间不能为空");
        this.updatedAt = now;
    }

    /**
     * 从持久化重建账单明细。
     */
    public CreditBillItem(
            String billId, String purchaseId, long amountFen,
            long allocatedPaidFen, long reversedFen,
            CreditBillItemStatus status, Instant createdAt, Instant updatedAt
    ) {
        this.billId = billId;
        this.purchaseId = purchaseId;
        this.amountFen = amountFen;
        this.allocatedPaidFen = allocatedPaidFen;
        this.reversedFen = reversedFen;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 应用还款分配金额。当已分配金额等于消费金额时标记为已还清。
     *
     * @param amountFen 分配金额（分），必须为正
     * @param now 当前时间
     */
    public void applyRepayment(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("分配金额必须为正");
        }
        if (this.allocatedPaidFen + amountFen > this.amountFen - this.reversedFen) {
            throw new IllegalStateException("分配金额超过明细未还余额");
        }
        this.allocatedPaidFen += amountFen;
        if (this.allocatedPaidFen + this.reversedFen >= this.amountFen) {
            this.status = CreditBillItemStatus.REPAID;
        }
        this.updatedAt = now;
    }

    /** @return 账单 ID */
    public String getBillId() { return billId; }

    /** @return 消费明细 ID */
    public String getPurchaseId() { return purchaseId; }

    /** @return 消费金额（分） */
    public long getAmountFen() { return amountFen; }

    /** @return 已分配还款金额（分） */
    public long getAllocatedPaidFen() { return allocatedPaidFen; }

    /** @return 已冲销金额（分） */
    public long getReversedFen() { return reversedFen; }

    /** @return 明细状态 */
    public CreditBillItemStatus getStatus() { return status; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }

    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }
}
