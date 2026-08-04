package com.minialalipay.account.domain.tcc;

/** 普通转账的账户与账本 TCC 分支类型。 */
public enum TccBranchType {
    /** 付款余额冻结、扣减或释放。 */ PAYER_BALANCE,
    /** 收款余额预占、入账或取消。 */ PAYEE_BALANCE,
    /** 复式账本凭证准备、过账或取消。 */ LEDGER
}
