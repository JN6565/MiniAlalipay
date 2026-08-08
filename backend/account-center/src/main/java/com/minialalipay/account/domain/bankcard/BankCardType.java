package com.minialalipay.account.domain.bankcard;

/**
 * 银行卡类型。
 *
 * <p>绑卡时由卡号 BIN 字典识别得出，绑定后不可变更；
 * 后续阶段「选卡支付/提现」会依据该类型决定扣款通道。</p>
 */
public enum BankCardType {
    /** 借记卡（储蓄卡）：先存款后消费，可作为充值/转账资金来源。 */
    DEBIT,
    /** 信用卡：先消费后还款，本期仅登记展示，不作为资金来源。 */
    CREDIT
}
