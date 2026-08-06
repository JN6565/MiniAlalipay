package com.minialalipay.business.domain.confirmation;

/**
 * 可签发确认令牌的业务主体类型。
 *
 * <p>确认令牌必须绑定明确的业务主体类型，避免不同支付场景相互消费同一份令牌。</p>
 */
public enum SubjectType {
    /** 主动转账草稿。 */
    TRANSFER_DRAFT,
    /** 动态扫码收款来源订单。 */
    QR_PAY_ORDER,
    /** 个人长期收款码生成的 C2C 订单。 */
    PERSONAL_QR_ORDER,
    /** 固定金额收款请求生成的 C2C 订单。 */
    COLLECTION_REQUEST_ORDER
}
