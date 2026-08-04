package com.minialalipay.business.domain.confirmation;

/** 一次性确认上下文状态。 */
public enum ConfirmationStatus {
    /** 当前有效且尚未消费。 */ ACTIVE,
    /** 已被一次资金受理事务消费，属于终态。 */ CONSUMED,
    /** 因重新签发、改密或主体变化被撤销，属于终态。 */ REVOKED,
    /** 超过两分钟有效期，属于终态。 */ EXPIRED
}
