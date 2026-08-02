package com.minialalipay.account.domain.ledger;

/**
 * 复式记账分录方向，用于区分借方和贷方；凭证过账时借贷金额必须相等。
 */
public enum LedgerDirection {
    /** 借方分录。 */
    DEBIT,

    /** 贷方分录。 */
    CREDIT
}
