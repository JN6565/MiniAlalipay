package com.minialalipay.account.domain.tcc;

/**
 * TCC分支动作类型，每个动作都必须按全局事务号和分支号保证幂等。
 */
public enum TccAction {
    /** 尝试阶段，校验并预留余额、额度或账本资源。 */
    TRY,

    /** 确认阶段，将已预留资源提交为最终资金事实。 */
    CONFIRM,

    /** 取消阶段，释放预留资源并处理空回滚。 */
    CANCEL
}
