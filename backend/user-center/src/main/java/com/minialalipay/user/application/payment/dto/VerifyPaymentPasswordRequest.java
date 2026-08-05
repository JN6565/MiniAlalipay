package com.minialalipay.user.application.payment.dto;

/**
 * 验证支付密码请求 DTO。
 *
 * <p>应用层的验证支付密码请求数据传输对象，用于 {@link com.minialalipay.user.application.payment.PaymentPasswordService#verifyPaymentPassword} 方法。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code paymentPassword} - 支付密码（必填，6 位数字）</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.application.payment.PaymentPasswordService#verifyPaymentPassword
 */
public record VerifyPaymentPasswordRequest(
        /**
         * 支付密码（必填，6 位数字）。
         * <p>用于验证身份，与存储的 BCrypt 哈希值比较。</p>
         */
        String paymentPassword
) {
}
