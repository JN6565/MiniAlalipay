package com.minialalipay.business.domain.transaction;

/**
 * 已受理资金交易的处理状态。
 *
 * <p>资金交易从{@link #PROCESSING}开始，只有终态发布器核对资金、账本和来源事实后
 * 才能进入确定终态。结果未知必须进入{@link #MANUAL_REVIEW}，禁止使用笼统失败终态。</p>
 */
public enum TransactionStatus {
    /** 交易已创建，TCC分支正在执行或等待终态核验。 */
    PROCESSING,

    /** 正在执行取消或补偿，资金可能仍处于冻结或部分完成状态；退款冲正使用独立REFUND交易。 */
    COMPENSATING,

    /** 自动恢复无法确定结果，需要人工核对，资金可能仍被冻结。 */
    MANUAL_REVIEW,

    /** 资金变化、账本分录和来源状态均已核验成功的终态。 */
    SUCCESS,

    /** 成功交易已经通过不可变反向分录完成冲正的终态。 */
    REVERSED,

    /** TCC取消或补偿已经完整完成，资金恢复后的终态。 */
    CANCELLED;

    /**
     * 判断资金交易是否已经得到确定结果，不再需要后台任务继续收敛。
     *
     * <p>{@link #SUCCESS}仍可通过独立REFUND交易和反向凭证变为{@link #REVERSED}，
     * 因此本方法不能用于拒绝退款或冲正。</p>
     *
     * @return 成功、已冲正或已撤销状态返回 {@code true}
     */
    public boolean hasDefinitiveOutcome() {
        return this == SUCCESS || this == REVERSED || this == CANCELLED;
    }
}
