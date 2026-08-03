package com.minialalipay.account.interfaces.credit.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 提交还款请求 DTO。
 *
 * @param repaymentDraftId 还款草稿 ID
 * @param paymentProofToken 支付密码证明令牌（不写入日志、URL 或浏览器存储）
 */
public record SubmitRepaymentRequest(
        @NotBlank String repaymentDraftId,
        @NotBlank String paymentProofToken
) {}
