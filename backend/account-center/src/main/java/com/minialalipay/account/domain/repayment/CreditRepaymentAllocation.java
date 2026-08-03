package com.minialalipay.account.domain.repayment;

import com.minialalipay.account.domain.credit.RepaymentAllocationType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 还款分配计划。
 *
 * <p>Try 阶段固化分配顺序与金额，Confirm 阶段不得重新计算。
 * 分配按固定优先级顺序：逾期账单 → 已出账账单 → 未出账消费。</p>
 */
public class CreditRepaymentAllocation {

    private final String repaymentId;
    private final int sequenceNo;
    private final RepaymentAllocationType targetType;
    private final String targetId;
    private final long amountFen;
    private final Instant createdAt;
    private final List<CreditRepaymentAllocationDetail> details;

    /**
     * 创建还款分配记录。
     *
     * @param repaymentId 还款 ID
     * @param sequenceNo 分配序号，从 1 开始
     * @param targetType 分配目标类型
     * @param targetId 目标 ID（账单 ID 或消费 ID）
     * @param amountFen 分配金额（分），必须为正
     * @param createdAt 创建时间
     */
    public CreditRepaymentAllocation(
            String repaymentId, int sequenceNo, RepaymentAllocationType targetType,
            String targetId, long amountFen, Instant createdAt
    ) {
        this.repaymentId = Objects.requireNonNull(repaymentId, "还款 ID 不能为空");
        this.sequenceNo = sequenceNo;
        this.targetType = Objects.requireNonNull(targetType, "分配目标类型不能为空");
        this.targetId = Objects.requireNonNull(targetId, "目标 ID 不能为空");
        if (amountFen <= 0) {
            throw new IllegalArgumentException("分配金额必须为正");
        }
        this.amountFen = amountFen;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.details = new ArrayList<>();
    }

    /** 添加分配明细 */
    public void addDetail(CreditRepaymentAllocationDetail detail) {
        this.details.add(Objects.requireNonNull(detail, "分配明细不能为空"));
    }

    /** @return 分配明细列表（不可变） */
    public List<CreditRepaymentAllocationDetail> getDetails() {
        return Collections.unmodifiableList(details);
    }

    /** @return 还款 ID */
    public String getRepaymentId() { return repaymentId; }

    /** @return 分配序号 */
    public int getSequenceNo() { return sequenceNo; }

    /** @return 分配目标类型 */
    public RepaymentAllocationType getTargetType() { return targetType; }

    /** @return 目标 ID */
    public String getTargetId() { return targetId; }

    /** @return 分配金额（分） */
    public long getAmountFen() { return amountFen; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }
}
