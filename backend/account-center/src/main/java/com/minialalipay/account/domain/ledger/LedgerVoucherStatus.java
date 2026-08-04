package com.minialalipay.account.domain.ledger;

/** 账本凭证状态，描述准备、过账、取消和被冲正的生命周期。 */
public enum LedgerVoucherStatus {
    /** 分录已准备但尚未形成最终账本事实，可进入 POSTED 或 CANCELLED。 */
    PREPARED,
    /** 借贷校验通过并过账的终态；不得修改或删除，只能通过新冲正凭证抵消。 */
    POSTED,
    /** 未过账凭证已取消的终态，不允许重试过账。 */
    CANCELLED,
    /** 原凭证已由新凭证完整冲正的终态，原分录仍永久保留。 */
    REVERSED
}
