package com.minialalipay.business.domain.collection;

/**
 * 固定金额收款请求的状态。
 *
 * <p>{@link #OPEN} 可被第一笔订单原子占用，{@link #RESERVED} 表示已有受理前订单占用；
 * 统一交易受理后由 {@link #PROCESSING} 表示在途，终态只能由交易终态发布器回填。</p>
 */
public enum CollectionRequestStatus {
    /** 请求可被任意付款人创建尝试订单。 */
    OPEN,
    /** 已由第一笔订单占用，后续订单不能进入资金受理。 */
    RESERVED,
    /** 唯一订单已进入统一交易，终态尚待资金事实核验。 */
    PROCESSING,
    /** 统一交易终态发布器确认资金成功后的终态。 */
    SUCCESS,
    /** 统一交易已完整取消后的终态。 */
    CANCELLED,
    /** 资金事实不一致时不得继续自动推进的终态。 */
    MANUAL_REVIEW,
    /** 收款方主动关闭的终态。 */
    CLOSED,
    /** 超过创建后 30 分钟的终态。 */
    EXPIRED
}
