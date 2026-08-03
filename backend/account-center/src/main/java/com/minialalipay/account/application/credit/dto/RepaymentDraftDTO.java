package com.minialalipay.account.application.credit.dto;

import java.time.Instant;
import java.util.List;

/**
 * 还款草稿 DTO。用于 POST /api/v1/credit/repayment-drafts 响应。
 *
 * @param repaymentDraftId 草稿 ID
 * @param amountFen 还款金额（分）
 * @param allocationHash 分配哈希（十六进制），确认时需匹配
 * @param expiresAt 过期时间
 * @param allocations 分配预览列表
 */
public record RepaymentDraftDTO(
        String repaymentDraftId,
        long amountFen,
        String allocationHash,
        Instant expiresAt,
        List<AllocationPreviewDTO> allocations
) {
    /**
     * 分配预览 DTO。
     *
     * @param sequenceNo 分配序号
     * @param targetType 目标类型（OVERDUE_BILL/BILL/UNBILLED_PURCHASE）
     * @param targetId 目标 ID
     * @param amountFen 分配金额（分）
     */
    public record AllocationPreviewDTO(
            int sequenceNo,
            String targetType,
            String targetId,
            long amountFen
    ) {}
}
