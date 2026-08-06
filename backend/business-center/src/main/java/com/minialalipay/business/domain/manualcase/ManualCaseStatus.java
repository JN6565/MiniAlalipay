package com.minialalipay.business.domain.manualcase;

/**
 * 人工工单生命周期状态。
 *
 * <p>{@link #OPEN} 可领取，{@link #CLAIMED} 只能由领取人处置，{@link #RESOLVED} 可因新证据重开，
 * {@link #CLOSED} 是不可重开的终态。</p>
 */
public enum ManualCaseStatus {
    /** 等待运营人员领取。 */
    OPEN,
    /** 已被一名运营人员领取。 */
    CLAIMED,
    /** 已处理但仍可因新证据重开。 */
    RESOLVED,
    /** 证据齐全且关闭后的终态。 */
    CLOSED
}
