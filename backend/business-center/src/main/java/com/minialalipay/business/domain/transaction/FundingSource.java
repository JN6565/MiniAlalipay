package com.minialalipay.business.domain.transaction;

/** 资金来源类型。 */
public enum FundingSource {
    /** 用户虚拟余额。 */ BALANCE,
    /** Mini 花呗虚拟信用额度。 */ MINI_CREDIT,
    /**
     * 系统模拟发行权益。
     * 仅供测试环境演示资金注入的模拟充值通道，C 端入口已下线；
     * 真实资金流入渠道仅为他人转账（BALANCE）与银行卡充值（BANK_CARD）。
     */ SYSTEM_ISSUANCE,
    /** 银行卡虚拟余额。 */ BANK_CARD
}
