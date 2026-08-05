package com.minialalipay.business.domain.transaction;

/** 资金来源类型。 */
public enum FundingSource {
    /** 用户虚拟余额。 */ BALANCE,
    /** Mini 花呗虚拟信用额度。 */ MINI_CREDIT,
    /** 系统模拟发行权益。 */ SYSTEM_ISSUANCE
}
