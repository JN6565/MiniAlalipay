package com.minialalipay.account.domain.repayment;

import java.time.Instant;
import java.util.Objects;

/**
 * 还款分配明细。逐笔指向消费及可选账单。
 *
 * <p>父分配金额必须等于其明细合计。</p>
 */
public class CreditRepaymentAllocationDetail {

    private final String repaymentId;
    private final int sequenceNo;
    private final int detailNo;
    private final String purchaseId;
    private final String billId;
    private final long amountFen;
    private final Instant createdAt;

    /**
     * 创建分配明细。
     *
     * @param repaymentId 还款 ID
     * @param sequenceNo 父分配序号
     * @param detailNo 明细序号，从 1 开始
     * @param purchaseId 消费明细 ID
     * @param billId 账单 ID，未出账消费分配时为 null
     * @param amountFen 明细金额（分），必须为正
     * @param createdAt 创建时间
     */
    public CreditRepaymentAllocationDetail(
            String repaymentId, int sequenceNo, int detailNo,
            String purchaseId, String billId, long amountFen, Instant createdAt
    ) {
        this.repaymentId = Objects.requireNonNull(repaymentId, "还款 ID 不能为空");
        this.sequenceNo = sequenceNo;
        this.detailNo = detailNo;
        this.purchaseId = Objects.requireNonNull(purchaseId, "消费明细 ID 不能为空");
        this.billId = billId;
        if (amountFen <= 0) {
            throw new IllegalArgumentException("明细金额必须为正");
        }
        this.amountFen = amountFen;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    /** @return 还款 ID */
    public String getRepaymentId() { return repaymentId; }

    /** @return 父分配序号 */
    public int getSequenceNo() { return sequenceNo; }

    /** @return 明细序号 */
    public int getDetailNo() { return detailNo; }

    /** @return 消费明细 ID */
    public String getPurchaseId() { return purchaseId; }

    /** @return 账单 ID，未出账消费分配时为 null */
    public String getBillId() { return billId; }

    /** @return 明细金额（分） */
    public long getAmountFen() { return amountFen; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }
}
