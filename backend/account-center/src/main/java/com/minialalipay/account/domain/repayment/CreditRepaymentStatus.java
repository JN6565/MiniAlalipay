package com.minialalipay.account.domain.repayment;

/**
 * 信用还款状态。
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link #PROCESSING}：还款提交后处理中</li>
 *   <li>{@link #SUCCESS}：还款 TCC Confirm 成功。终态。</li>
 *   <li>{@link #CANCELLED}：还款 TCC Cancel 成功或主动取消。终态。</li>
 *   <li>{@link #MANUAL_REVIEW}：无法自动判定，需人工处理</li>
 * </ul>
 * {@link #SUCCESS} 和 {@link #CANCELLED} 为终态。</p>
 */
public enum CreditRepaymentStatus {
    /** 还款提交后处理中。 */
    PROCESSING,

    /** 还款 TCC Confirm 成功。终态。 */
    SUCCESS,

    /** 还款 TCC Cancel 成功或主动取消。终态。 */
    CANCELLED,

    /** 无法自动判定，需人工处理。 */
    MANUAL_REVIEW
}
