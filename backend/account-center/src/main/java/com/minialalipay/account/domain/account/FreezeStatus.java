package com.minialalipay.account.domain.account;

/** 余额冻结记录状态，约束 Try、Confirm、Cancel 的幂等状态流转。 */
public enum FreezeStatus {
    /** Try 已完成的活动状态；后续只允许确认或释放，允许相同 Try 幂等重试。 */
    FROZEN,
    /** Confirm 已完成的终态；相同 Confirm 允许幂等重试，不可再释放。 */
    CONFIRMED,
    /** Cancel 已完成的终态；相同 Cancel 允许幂等重试，不可再次确认或冻结。 */
    RELEASED
}
