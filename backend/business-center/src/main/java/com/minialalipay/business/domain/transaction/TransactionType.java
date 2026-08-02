package com.minialalipay.business.domain.transaction;

/**
 * 统一资金交易的业务类型，用于选择受理规则、TCC参与者和账本模板。
 */
public enum TransactionType {
    /** 普通用户主动转账、个人码付款或固定请求付款，资金来源只能是虚拟余额。 */
    TRANSFER,

    /** 动态扫码订单使用虚拟余额付款。 */
    QR_PAY,

    /** 动态扫码订单使用Mini花呗额度付款。 */
    CREDIT_PAY,

    /** 用户使用本人虚拟余额偿还Mini花呗应收。 */
    CREDIT_REPAY,

    /** 模拟充值通过系统发行权益向普通用户虚拟余额入账。 */
    RECHARGE,

    /** 对原扫码支付执行受控全额虚拟退款，并关联原交易和冲正凭证。 */
    REFUND
}
