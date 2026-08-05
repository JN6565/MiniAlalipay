package com.minialalipay.ai.domain.agent;

/**
 * AI 服务幂等记录状态。
 *
 * <ul>
 *   <li>{@link #PROCESSING} — 请求已受理但尚未完成，不可覆盖</li>
 *   <li>{@link #COMPLETED} — 已成功完成，响应快照可供重复请求返回（终态）</li>
 *   <li>{@link #FAILED} — 已明确失败，可选择性允许过期后重试（终态）</li>
 * </ul>
 *
 * <p>任何状态均不可被新摘要覆盖。同键异参必须返回 {@code IDEMPOTENCY_CONFLICT}。</p>
 */
public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
