package com.minialalipay.account.domain.credit;

/**
 * 信用还款分配目标类型。
 *
 * <p>还款分配按固定优先级顺序进行：
 * <ol>
 *   <li>{@link #OVERDUE_BILL}：逾期账单，按最早到期时间排序</li>
 *   <li>{@link #BILL}：已出账账单，按最早出账时间排序</li>
 *   <li>{@link #UNBILLED_PURCHASE}：未出账消费，按最早发生时间排序</li>
 * </ol>
 * Try 阶段固化分配顺序与金额，Confirm 阶段不得重新计算。</p>
 */
public enum RepaymentAllocationType {
    /** 逾期账单分配。 */
    OVERDUE_BILL,

    /** 已出账账单分配。 */
    BILL,

    /** 未出账消费分配。 */
    UNBILLED_PURCHASE
}
