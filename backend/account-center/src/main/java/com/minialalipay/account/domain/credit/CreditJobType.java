package com.minialalipay.account.domain.credit;

/**
 * 信用定时任务类型。
 */
public enum CreditJobType {
    /** 月度出账任务。 */
    STATEMENT,

    /** 到期检查任务。 */
    DUE_CHECK
}
