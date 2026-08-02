package com.minialalipay.business.domain.risk;

/**
 * 前置风控决策结果；该决策发生在资金交易创建之前。
 */
public enum RiskDecisionStatus {
    /** 风控校验通过，可以继续进入可信确认流程。 */
    PASS,

    /** 风控明确拒绝，禁止创建资金交易或冻结资金。 */
    REJECT,

    /** 需要运营人员审核，审核期间不得创建资金交易或冻结资金。 */
    MANUAL_REVIEW
}
