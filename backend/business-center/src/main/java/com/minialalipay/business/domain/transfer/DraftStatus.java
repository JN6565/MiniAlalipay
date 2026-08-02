package com.minialalipay.business.domain.transfer;

/**
 * 转账草稿生命周期状态。
 *
 * <p>正常顺序为{@code DRAFT -> VALIDATED -> PENDING_CONFIRMATION -> SUBMITTED}；
 * 未提交草稿可以进入{@code EXPIRED}。草稿字段变化必须递增版本、回到待校验阶段并使旧确认失效，
 * 草稿状态不能替代资金交易状态。</p>
 */
public enum DraftStatus {
    /** 草稿可编辑，尚未完成可信确认。 */
    DRAFT,

    /** 草稿已经完成收款人、账户、金额和风控预校验，仍可因字段修改重新校验。 */
    VALIDATED,

    /** 草稿字段已经锁定，等待用户在可信UI完成支付密码和最终确认。 */
    PENDING_CONFIRMATION,

    /** 草稿已经提交并关联统一资金交易，不得再次编辑或提交。 */
    SUBMITTED,

    /** 草稿超过有效期，不能继续确认或提交。 */
    EXPIRED
}
