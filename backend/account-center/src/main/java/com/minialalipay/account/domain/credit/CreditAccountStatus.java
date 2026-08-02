package com.minialalipay.account.domain.credit;

/**
 * Mini花呗信用账户状态。
 */
public enum CreditAccountStatus {
    /** 信用账户正常，可在额度充足且无逾期限制时发起新的信用支付。 */
    ACTIVE,

    /** 信用账户已暂停，禁止新增信用支付，但允许查询和余额还款。 */
    SUSPENDED,

    /** 信用账户已关闭，不再受理新的信用业务。 */
    CLOSED
}
