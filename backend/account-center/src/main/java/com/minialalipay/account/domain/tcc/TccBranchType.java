package com.minialalipay.account.domain.tcc;

/** 账户中心负责持久化的余额、账本与信用 TCC 分支类型。 */
public enum TccBranchType {
    /** 付款余额冻结、扣减或释放。 */ PAYER_BALANCE,
    /** 收款余额预占、入账或取消。 */ PAYEE_BALANCE,
    /** 复式账本凭证准备、过账或取消。 */ LEDGER,
    /** Mini 花呗支付额度冻结、确认应收或释放。 */ CREDIT_PAY,
    /** Mini 花呗还款余额冻结、应收扣减或释放。 */ CREDIT_REPAY
}
