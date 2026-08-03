package com.minialalipay.account.application.credit.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 信用账单详情 DTO。用于 GET /api/v1/credit/bills/{id} 响应。
 *
 * @param billId 账单 ID
 * @param period 账期（yyyy-MM）
 * @param statementDate 出账日期
 * @param dueAt 到期时间
 * @param totalFen 账单总额（分）
 * @param paidFen 已还金额（分）
 * @param outstandingFen 未还金额（分）
 * @param status 账单状态（OPEN/PARTIALLY_PAID/PAID/OVERDUE）
 * @param items 账单明细列表
 */
public record CreditBillDetailDTO(
        String billId,
        String period,
        LocalDate statementDate,
        Instant dueAt,
        long totalFen,
        long paidFen,
        long outstandingFen,
        String status,
        List<BillItemDTO> items
) {
    /**
     * 账单明细 DTO。
     *
     * @param purchaseId 消费明细 ID
     * @param amountFen 消费金额（分）
     * @param allocatedPaidFen 已分配还款金额（分）
     * @param status 明细状态（ACTIVE/REPAID/REVERSED）
     */
    public record BillItemDTO(
            String purchaseId,
            long amountFen,
            long allocatedPaidFen,
            String status
    ) {}
}
