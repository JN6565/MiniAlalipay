package com.minialalipay.account.domain.ledger;

/** 账本科目状态；关闭科目只阻止新分录，不影响历史查询。 */
public enum LedgerAccountStatus {
    /** 活动状态，可接受符合业务模板的新分录。 */
    ACTIVE,
    /** 关闭终态，不可新增分录，历史分录永久保留。 */
    CLOSED
}
