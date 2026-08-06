package com.minialalipay.business.domain.manualcase;

/** 人工工单类型，区分资金受理前风控与资金恢复处置。 */
public enum ManualCaseType {
    /** 资金受理前的风险复核，批准后用户必须重新确认。 */
    RISK_PRECHECK,
    /** 已创建交易但未收敛时的恢复处置。 */
    TRANSACTION_RECOVERY
}
