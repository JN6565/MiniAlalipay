package com.minialalipay.user.domain.identity;

/**
 * 用户模拟身份信息的核验状态，不代表登录会话或账户状态。
 */
public enum IdentityStatus {
    /** 身份信息已提交，等待核验。 */
    PENDING_VERIFICATION,

    /** 身份信息核验通过。 */
    VERIFIED,

    /** 身份信息核验未通过，需要修正后重新提交。 */
    REJECTED
}
