package com.minialalipay.business.domain.recharge;

/**
 * 模拟充值来源订单状态。
 *
 * <p>本阶段仅创建 {@link #PENDING_CHANNEL} 或记录渠道拒绝；{@link #PROCESSING} 只能在后续统一交易端口
 * 成功受理后写入，不能代表充值成功。</p>
 */
public enum RechargeOrderStatus {
    /** 限额已预占，等待受控模拟渠道结果。 */
    PENDING_CHANNEL,
    /** 后续统一资金交易已受理，等待账户与账本事实收敛。 */
    PROCESSING,
    /** 账户和账本事实验证后的终态，当前来源聚合不自行进入。 */
    SUCCESS,
    /** 渠道或前置校验拒绝的终态，必须释放处理中日额度。 */
    REJECTED,
    /** 受理前取消或后续完整补偿后的终态。 */
    CANCELLED,
    /** 需要运营处理的非确定状态。 */
    MANUAL_REVIEW
}
