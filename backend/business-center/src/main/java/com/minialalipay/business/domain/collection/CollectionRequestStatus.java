package com.minialalipay.business.domain.collection;

/**
 * 固定金额收款请求的状态。
 *
 * <p>一码多收模型下 {@link #OPEN} 表示尚可受理新订单，{@link #PROCESSING} 表示已有订单进入
 * 统一交易受理（可多笔并行），终态只能由交易终态发布器回填。</p>
 */
public enum CollectionRequestStatus {
    /** 请求可被任意付款人创建订单，且仍可受理新的支付。 */
    OPEN,
    /** 已弃用：旧单笔占用模型的受理前仲裁状态，新流程不再产生，仅为兼容既有数据保留。 */
    RESERVED,
    /** 已有订单进入统一交易（允许多笔并行受理），终态尚待资金事实核验。 */
    PROCESSING,
    /** 统一交易终态发布器确认资金成功后的终态。 */
    SUCCESS,
    /** 统一交易已完整取消后的终态。 */
    CANCELLED,
    /** 资金事实不一致时不得继续自动推进的终态。 */
    MANUAL_REVIEW,
    /** 超过创建后 30 分钟的终态。 */
    EXPIRED
}
