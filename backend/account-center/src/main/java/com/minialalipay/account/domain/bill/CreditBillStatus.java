package com.minialalipay.account.domain.bill;

/**
 * 信用月度账单状态。
 *
 * <p>状态流转规则：
 * <ul>
 *   <li>{@link #OPEN}：账单刚生成，未发生还款</li>
 *   <li>{@link #PARTIALLY_PAID}：账单已部分还款</li>
 *   <li>{@link #PAID}：账单已全额还款。终态。</li>
 *   <li>{@link #OVERDUE}：账单到期未还清，转为逾期</li>
 * </ul>
 * 流转路径：
 * <pre>
 * OPEN --部分还款--> PARTIALLY_PAID
 * OPEN --全额还款--> PAID
 * PARTIALLY_PAID --全额还款--> PAID
 * OPEN/PARTIALLY_PAID --到期未还清--> OVERDUE
 * OVERDUE --部分还款--> PARTIALLY_PAID
 * OVERDUE --全额还款--> PAID
 * </pre>
 * {@link #PAID} 为终态，不可回退。</p>
 */
public enum CreditBillStatus {
    /** 账单刚生成，未发生还款。 */
    OPEN,

    /** 账单已部分还款，仍有未还余额。 */
    PARTIALLY_PAID,

    /** 账单已全额还款。终态。 */
    PAID,

    /** 账单到期未还清，转为逾期。 */
    OVERDUE
}
