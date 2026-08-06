package com.minialalipay.business.domain.refund;

/**
 * 受控退款来源订单状态。
 *
 * <p>订单只管理创建与受理前推进；{@link #SUCCESS}、{@link #MANUAL_REVIEW} 等终态
 * 必须由统一资金交易终态发布器回填，来源订单不得自行判定资金结果。
 * 与远程 {@code refund_order} 表状态约束保持一致。</p>
 */
public enum RefundOrderStatus {
    /** 已创建并校验原交易，等待提交执行退款。 */
    CREATED,
    /** 已受理 REFUND 统一交易，等待账户与账本事实收敛。 */
    PROCESSING,
    /** 统一资金交易终态成功后的退款完成。 */
    SUCCESS,
    /** 前置校验拒绝或统一交易失败补偿后的终态。 */
    REJECTED,
    /** 提交前取消或完整补偿后的终态。 */
    CANCELLED,
    /** 需要运营处理的人工复核状态。 */
    MANUAL_REVIEW
}
