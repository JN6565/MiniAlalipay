package com.minialalipay.account.domain.credit;

/**
 * 信用定时任务执行状态。
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link #PENDING}：任务已创建，等待执行</li>
 *   <li>{@link #RUNNING}：任务正在执行中</li>
 *   <li>{@link #SUCCESS}：任务执行成功。终态。</li>
 *   <li>{@link #FAILED}：任务执行失败。终态。</li>
 *   <li>{@link #MANUAL_REVIEW}：需人工处理。终态。</li>
 * </ul>
 * {@link #SUCCESS}、{@link #FAILED} 和 {@link #MANUAL_REVIEW} 为终态。</p>
 */
public enum CreditJobStatus {
    /** 任务已创建，等待执行。 */
    PENDING,

    /** 任务正在执行中。 */
    RUNNING,

    /** 任务执行成功。终态。 */
    SUCCESS,

    /** 任务执行失败。终态。 */
    FAILED,

    /** 需人工处理。终态。 */
    MANUAL_REVIEW
}
