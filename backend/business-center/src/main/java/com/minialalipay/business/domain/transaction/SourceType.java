package com.minialalipay.business.domain.transaction;

/**
 * 统一资金交易来源类型。
 *
 * <p>每个来源对象通过 {@code source_type + source_order_id} 最多映射一笔资金交易。</p>
 */
public enum SourceType {
    /** 主动转账草稿。 */
    TRANSFER_DRAFT,
    /** 动态扫码收款来源订单；余额与信用支付共用该唯一来源。 */
    QR_PAY_ORDER,
    /** 个人长期收款码的 C2C 订单；余额与信用支付共用该唯一来源。 */
    PERSONAL_QR_ORDER,
    /** 固定金额收款请求的 C2C 订单；余额与信用支付共用该唯一来源。 */
    COLLECTION_REQUEST_ORDER,
    /** 受控模拟充值订单。 */
    RECHARGE_ORDER,
    /** 受控退款来源订单。 */
    REFUND_ORDER
}
