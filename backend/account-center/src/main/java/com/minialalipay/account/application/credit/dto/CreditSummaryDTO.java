package com.minialalipay.account.application.credit.dto;

/**
 * 信用额度摘要 DTO。用于 GET /api/v1/credit/me 响应。
 *
 * @param creditAccountId 信用账户 ID
 * @param status 账户状态（ACTIVE/SUSPENDED/CLOSED）
 * @param totalLimitFen 总额度（分）
 * @param usedFen 已用额度（分）
 * @param frozenFen 冻结额度（分）
 * @param availableFen 可用额度（分）
 * @param unbilledFen 未出账应收（分）
 * @param billedFen 已出账应收（分）
 * @param overdueFen 逾期应收（分）
 */
public record CreditSummaryDTO(
        String creditAccountId,
        String status,
        long totalLimitFen,
        long usedFen,
        long frozenFen,
        long availableFen,
        long unbilledFen,
        long billedFen,
        long overdueFen
) {}
