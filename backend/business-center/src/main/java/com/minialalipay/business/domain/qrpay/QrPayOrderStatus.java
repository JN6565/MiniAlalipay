package com.minialalipay.business.domain.qrpay;

/**
 * 动态扫码支付来源订单的交互状态。
 *
 * <p>受理前按{@code CREATED -> SCANNED -> PENDING_CONFIRMATION}推进，风控人工审核
 * 使用{@code RISK_REVIEW}；受理后只能根据统一交易事实进入处理、补偿、人工恢复或确定结果，
 * 来源订单不得自行推断成功。</p>
 */
public enum QrPayOrderStatus {
    /** 收款订单已创建，动态二维码令牌尚未完成交换。 */
    CREATED,

    /** 二维码已由合法H5会话交换，等待付款人确认。 */
    SCANNED,

    /** 收款人、金额和资金来源已锁定，等待支付密码与用户确认。 */
    PENDING_CONFIRMATION,

    /** 前置风控需要人工审核，尚未创建资金交易或冻结资金。 */
    RISK_REVIEW,

    /** 确认已受理并关联统一资金交易，当前结果尚未确定。 */
    PROCESSING,

    /** 资金交易正在取消或补偿，订单结果尚未确定；退款不改变原扫码订单的支付成功状态。 */
    COMPENSATING,

    /** 已受理资金交易无法自动收敛，需要人工恢复，资金可能仍被冻结。 */
    MANUAL_REVIEW,

    /** 终态发布器已验证资金和账本事实，订单支付成功。 */
    SUCCESS,

    /** 订单在创建资金交易前被校验或风控拒绝，资金没有变化。 */
    REJECTED,

    /** 订单在受理前取消，或已受理交易完成全部取消和补偿。 */
    CANCELLED,

    /** 订单或二维码令牌在受理前已过期，不再允许创建资金交易。 */
    EXPIRED
}
