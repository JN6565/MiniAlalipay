package com.minialalipay.account.domain.repayment;

/**
 * 信用还款草稿状态。
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link #DRAFT}：草稿刚创建，等待提交</li>
 *   <li>{@link #CONFIRMED}：草稿已确认，等待 TCC 提交</li>
 *   <li>{@link #CONSUMED}：草稿已被还款交易消费。终态。</li>
 *   <li>{@link #EXPIRED}：草稿已过期。终态。</li>
 * </ul>
 * {@link #CONSUMED} 和 {@link #EXPIRED} 为终态。</p>
 */
public enum CreditRepaymentDraftStatus {
    /** 草稿刚创建，等待提交。 */
    DRAFT,

    /** 草稿已确认，等待 TCC 提交。 */
    CONFIRMED,

    /** 草稿已被还款交易消费。终态。 */
    CONSUMED,

    /** 草稿已过期。终态。 */
    EXPIRED
}
