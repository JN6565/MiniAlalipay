package com.minialalipay.business.domain.collection;

import java.time.Instant;
import java.util.Objects;

/**
 * 固定收款请求可重放的最小公开状态事件。
 *
 * <p>事件仅用于 SSE 回放，不能携带账户、会话、令牌、支付证明或确认上下文。{@code eventId}
 * 在保留期内可作为 {@code Last-Event-ID} 游标；超过保留期的游标必须要求客户端回源查询。</p>
 */
public record CollectionOrderEvent(
        String eventId,
        String requestId,
        String activeOrderId,
        String transactionId,
        String status,
        Instant occurredAt
) {
    /** 创建经过边界校验的 SSE 公开事件。 */
    public CollectionOrderEvent {
        require(eventId, "事件 ID");
        require(requestId, "固定收款请求 ID");
        require(status, "公开状态");
        Objects.requireNonNull(occurredAt, "发生时间不能为空");
    }

    private static void require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
    }
}
