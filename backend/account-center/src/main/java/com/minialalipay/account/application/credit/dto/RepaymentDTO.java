package com.minialalipay.account.application.credit.dto;

import java.time.Instant;

/**
 * 还款记录 DTO。用于 POST /api/v1/credit/repayments 和 GET /api/v1/credit/repayments/{id} 响应。
 *
 * @param repaymentId 还款 ID
 * @param amountFen 还款金额（分）
 * @param status 还款状态（PROCESSING/SUCCESS/CANCELLED/MANUAL_REVIEW）
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record RepaymentDTO(
        String repaymentId,
        long amountFen,
        String status,
        Instant createdAt,
        Instant updatedAt
) {}
