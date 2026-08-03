package com.minialalipay.account.domain.bill;

/**
 * 账单明细（账单与信用消费关联记录）的状态。
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link #ACTIVE}：明细活跃，消费已纳入账单但未全额还清</li>
 *   <li>{@link #REPAID}：明细对应消费已全额还清。终态。</li>
 *   <li>{@link #REVERSED}：明细被退款冲正。终态。</li>
 * </ul>
 * </p>
 */
public enum CreditBillItemStatus {
    /** 明细活跃，消费已纳入账单但未全额还清。 */
    ACTIVE,

    /** 明细对应消费已全额还清。终态。 */
    REPAID,

    /** 明细被退款冲正。终态。 */
    REVERSED
}
