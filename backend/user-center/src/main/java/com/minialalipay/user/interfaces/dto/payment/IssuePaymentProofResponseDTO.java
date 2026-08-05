package com.minialalipay.user.interfaces.dto.payment;

/**
 * 签发支付证明响应 DTO。
 *
 * @param accessToken 原始令牌（客户端需要保存，用于后续确认）
 */
public record IssuePaymentProofResponseDTO(
        String accessToken
) {
}
