package com.minialalipay.account.domain.tcc;

/**
 * TCC参与者分支状态。
 *
 * <p>{@link #CONFIRMED}和{@link #CANCELLED}是互斥资金终态，重复回调必须返回原结果；
 * 无法自动判定的分支进入{@link #MANUAL_REVIEW}并保持资源占用。</p>
 */
public enum TccBranchStatus {
    /** 分支已经持久化但Try尚未成功；Cancel先到时可从此状态记录空回滚屏障。 */
    INIT,

    /** Try已经成功并预留资源，后续只能确认、取消或转人工处理。 */
    TRIED,

    /** Confirm已经完成，分支结果为成功终态。 */
    CONFIRMED,

    /** Cancel已经完成，分支结果为撤销终态；也用于记录已处理的空回滚。 */
    CANCELLED,

    /** 分支结果无法自动收敛，需要人工核对，不能按成功或取消处理。 */
    MANUAL_REVIEW
}
