package com.minialalipay.account.domain.ledger;

/** 账本科目所有者类型，用于区分系统、用户和信用聚合的会计主体。 */
public enum LedgerOwnerType {
    /** 系统发行或清算科目，不对应登录用户。 */
    SYSTEM,
    /** 普通用户本人虚拟余额科目。 */
    USER,
    /** 历史兼容主体；当前 MVP 禁止新建该类科目。 */
    MERCHANT,
    /** Mini 花呗信用账户主体，独立于用户余额。 */
    CREDIT_ACCOUNT
}
