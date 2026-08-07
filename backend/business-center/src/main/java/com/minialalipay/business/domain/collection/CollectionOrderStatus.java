package com.minialalipay.business.domain.collection;

/**
 * 个人码或固定收款请求创建的 C2C 来源订单状态。
 *
 * <p>{@link #DRAFT} 和 {@link #PENDING_CONFIRMATION} 管理受理前状态；{@link #PROCESSING}
 * 仅表示统一交易已受理。成功、取消和人工审核终态只能由统一交易终态发布器回填。</p>
 */
public enum CollectionOrderStatus {
    /** 个人码订单等待付款方填写金额和主题。 */
    DRAFT,
    /** 金额、双方账户和付款方已锁定，等待风控与确认。 */
    PENDING_CONFIRMATION,
    /** 命中前置风控转人工复核，受理前拦截；不创建交易或冻结资金。 */
    RISK_REVIEW,
    /** 后续统一交易已受理，终态仍待资金事实回填。 */
    PROCESSING,
    /** 统一交易核验全部资金事实后发布的成功终态。 */
    SUCCESS,
    /** 统一交易资金事实失败后的终态，由终态发布器回填。 */
    FAILED,
    /** 统一交易已完整取消后的终态，不代表可以绕过仲裁重新受理。 */
    CANCELLED,
    /** 资金事实不一致时冻结在人工审核的终态。 */
    MANUAL_REVIEW,
    /** 受理前过期的终态。 */
    EXPIRED
}
