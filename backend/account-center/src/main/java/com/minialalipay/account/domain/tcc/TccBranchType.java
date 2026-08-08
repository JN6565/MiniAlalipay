package com.minialalipay.account.domain.tcc;

/** 账户中心负责持久化的余额、账本与信用 TCC 分支类型。 */
public enum TccBranchType {
    /** 付款余额冻结、扣减或释放。 */ PAYER_BALANCE,
    /** 收款余额预占、入账或取消。 */ PAYEE_BALANCE,
    /** 普通转账复式账本凭证。 */ LEDGER,
    /** 信用支付专用账本凭证。 */ CREDIT_PAY_LEDGER,
    /** Mini 花呗支付额度分支。 */ CREDIT_PAY,
    /** Mini 花呗还款余额分支。 */ CREDIT_REPAY,
    /** 充值入账分支。 */ RECHARGE,
    /** 信用支付退款冲正分支（核销消费明细与应收）。 */ REFUND,
    /** 退款专用复式账本凭证。 */ REFUND_LEDGER
}
