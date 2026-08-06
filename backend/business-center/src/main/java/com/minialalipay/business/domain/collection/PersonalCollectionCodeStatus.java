package com.minialalipay.business.domain.collection;

/**
 * 个人收款码的生命周期状态。
 *
 * <p>每个用户只能有一个 {@link #ACTIVE} 码；换码后旧码为 {@link #REPLACED}，停用后为
 * {@link #DISABLED}，两者均不可再创建收款订单。</p>
 */
public enum PersonalCollectionCodeStatus {
    /** 唯一可用于创建个人码订单的状态。 */
    ACTIVE,
    /** 已被新码替换，不允许恢复或使用。 */
    REPLACED,
    /** 收款方停用后的终态。 */
    DISABLED
}
