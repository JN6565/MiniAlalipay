package com.minialalipay.account.application.credit.dto;

import java.time.Instant;

/**
 * 信用消费明细 DTO。用于 GET /api/v1/credit/purchases 响应列表项。
 *
 * @param purchaseId 消费明细 ID
 * @param creditTransactionId 信用支付交易 ID
 * @param qrOrderId 扫码订单 ID
 * @param amountFen 消费金额（分）
 * @param repaidFen 已还金额（分）
 * @param outstandingFen 未还余额（分）
 * @param billingStatus 出账状态（UNBILLED/BILLED/REPAID/REVERSED）
 * @param occurredAt 发生时间
 */
public record CreditPurchaseDTO(
        String purchaseId,
        String creditTransactionId,
        String qrOrderId,
        long amountFen,
        long repaidFen,
        long outstandingFen,
        String billingStatus,
        Instant occurredAt
) {}
