package com.minialalipay.account.domain.account;

/**
 * 虚拟余额账户的生命周期状态。
 */
public enum AccountStatus {
    /** 账户正常，可在通过鉴权、风控和余额校验后发起扣款。 */
    ACTIVE,

    /** 账户被冻结，禁止新扣款，但仍允许查询及执行必要的恢复操作。 */
    FROZEN,

    /** 账户已关闭，不再受理新的资金业务。 */
    CLOSED;

    /**
     * 判断当前状态是否允许发起新的扣款。
     *
     * @return 仅正常状态返回 {@code true}
     */
    public boolean allowsDebit() {
        return this == ACTIVE;
    }
}
