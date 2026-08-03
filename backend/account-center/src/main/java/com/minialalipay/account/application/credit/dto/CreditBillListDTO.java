package com.minialalipay.account.application.credit.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 信用账单列表项 DTO。用于 GET /api/v1/credit/bills 响应列表项。
 *
 * @param billId 账单 ID
 * @param period 账期（yyyy-MM）
 * @param statementDate 出账日期
 * @param dueAt 到期时间
 * @param totalFen 账单总额（分）
 * @param paidFen 已还金额（分）
 * @param outstandingFen 未还金额（分）
 * @param status 账单状态
 */
public record CreditBillListDTO(
        String billId,
        String period,
        LocalDate statementDate,
        Instant dueAt,
        long totalFen,
        long paidFen,
        long outstandingFen,
        String status
) {}
