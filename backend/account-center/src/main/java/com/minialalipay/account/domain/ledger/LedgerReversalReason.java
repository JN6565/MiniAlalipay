package com.minialalipay.account.domain.ledger;

/** 冲正原因，确保新凭证可以追溯到明确、受控的修复意图。 */
public enum LedgerReversalReason {
    /** 原业务发生受控退款，使用反向分录抵消原凭证。 */
    BUSINESS_REFUND,
    /** 对账发现证账实差异，经处置后创建冲正凭证。 */
    RECONCILIATION,
    /** 系统缺陷或数据修复产生的受控纠正，不允许直接修改原分录。 */
    SYSTEM_CORRECTION
}
