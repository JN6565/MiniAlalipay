package com.minialalipay.user.interfaces.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 签发支付证明请求 DTO。
 *
 * @param paymentPassword 支付密码（6 位数字）
 * @param purpose         确认用途（如 TRANSFER、QR_PAY 等）
 */
public record IssuePaymentProofRequestDTO(
        @NotBlank(message = "支付密码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "支付密码必须为 6 位数字")
        String paymentPassword,

        @NotBlank(message = "确认用途不能为空")
        String purpose
) {
}
