package com.minialalipay.account.domain.account;

/** 冻结用途，用于隔离同一交易内不同余额资源的幂等记录。 */
public enum FreezePurpose {
    /** 转账或余额扫码的付款方扣款冻结。 */
    TRANSFER_OUT,
    /** 信用还款使用本人余额时的扣款冻结。 */
    CREDIT_REPAYMENT,
    /** 退款或冲正流程需要延后确认的余额冻结。 */
    REFUND
}
