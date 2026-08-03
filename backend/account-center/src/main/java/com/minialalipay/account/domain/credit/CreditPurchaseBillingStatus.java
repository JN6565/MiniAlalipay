package com.minialalipay.account.domain.credit;

/**
 * 信用消费明细的出账状态。
 *
 * <p>状态流转规则：
 * <ul>
 *   <li>{@link #UNBILLED}：消费刚创建，尚未进入月度账单</li>
 *   <li>{@link #BILLED}：月度出账任务已将消费汇总到账单</li>
 *   <li>{@link #REPAID}：消费已全额还清（含提前还款和账单还款后的剩余清零）</li>
 *   <li>{@link #REVERSED}：消费被退款冲正</li>
 * </ul>
 * {@link #REPAID} 和 {@link #REVERSED} 为终态。</p>
 */
public enum CreditPurchaseBillingStatus {
    /** 消费尚未出账，属于未出账应收。 */
    UNBILLED,

    /** 消费已汇入月度账单，属于已出账应收。 */
    BILLED,

    /** 消费已全额还清。终态。 */
    REPAID,

    /** 消费被退款冲正。终态。 */
    REVERSED
}
