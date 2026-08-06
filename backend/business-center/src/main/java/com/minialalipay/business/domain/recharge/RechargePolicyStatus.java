package com.minialalipay.business.domain.recharge;

/** 模拟充值策略状态；只有活动策略可以用于创建充值订单。 */
public enum RechargePolicyStatus {
    /** 当前生效的唯一策略版本。 */
    ACTIVE,
    /** 历史或尚未启用的策略版本。 */
    INACTIVE
}
