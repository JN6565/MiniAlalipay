package com.minialalipay.account.domain.credit;

/**
 * 信用支付 TCC 冻结记录状态。
 *
 * <p>冻结记录在 Try 阶段创建为 {@link #FROZEN}，Confirm 后转为 {@link #CONFIRMED}，
 * Cancel 后转为 {@link #RELEASED}。三种状态之间的转换不可逆，
 * 重复调用同一阶段必须幂等返回原结果。</p>
 */
public enum CreditFreezeStatus {
    /** Try 阶段已冻结额度，等待 Confirm 或 Cancel。 */
    FROZEN,

    /** Confirm 已完成，冻结金额已转为已用额度并形成信用应收。终态。 */
    CONFIRMED,

    /** Cancel 已完成，冻结额度已释放回可用额度。终态。 */
    RELEASED
}
