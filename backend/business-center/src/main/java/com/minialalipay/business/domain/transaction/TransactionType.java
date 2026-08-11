package com.minialalipay.business.domain.transaction;

/**
 * 统一资金交易的业务类型，用于选择受理规则、TCC参与者和账本模板。
 */
public enum TransactionType {
    /** 普通用户主动转账，或个人码、固定请求使用虚拟余额付款。 */
    TRANSFER,

    /** 动态扫码订单使用虚拟余额付款。 */
    QR_PAY,

    /** 动态扫码、个人码或固定请求订单使用 Mini 花呗额度付款。 */
    CREDIT_PAY,

    /** 用户使用本人虚拟余额偿还Mini花呗应收。 */
    CREDIT_REPAY,

    /** 模拟充值通过系统发行权益向普通用户虚拟余额入账。 */
    RECHARGE,

    /** 对原扫码支付执行受控全额虚拟退款，并关联原交易和冲正凭证。 */
    REFUND,

    /** 银行卡充值（银行卡给账户充钱）：银行卡余额减少，账户余额同步增加。 */
    BANK_CARD_RECHARGE,

    /** 银行卡提现（账户给银行卡充钱）：账户余额减少，银行卡余额同步增加。 */
    BANK_CARD_WITHDRAW
}
