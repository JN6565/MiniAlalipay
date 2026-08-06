package com.minialalipay.business.domain.qrpay;

import java.time.Instant;
import java.util.Objects;

/**
 * 动态扫码订单用于 SSE 重放的最小公开状态事件。
 *
 * <p>事件只包含订单、统一交易、状态和发生时间；不得保存或发送二维码原始令牌、H5 会话、账户、支付证明或确认令牌。</p>
 */
public record QrPayOrderEvent(String eventId, String qrOrderId, String transactionId, String status, Instant occurredAt) {
    /** 创建经过边界校验的二维码订单公开事件。 */
    public QrPayOrderEvent {
        require(eventId, "事件 ID");
        require(qrOrderId, "二维码订单 ID");
        require(status, "公开状态");
        Objects.requireNonNull(occurredAt, "发生时间不能为空");
    }

    private static void require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
    }
}
