package com.minialalipay.user.application.payment.dto;

/**
 * 设置支付密码请求 DTO。
 *
 * <p>应用层的设置支付密码请求数据传输对象，用于 {@link com.minialalipay.user.application.payment.PaymentPasswordService#setPaymentPassword} 方法。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code paymentPassword} - 支付密码（必填，6 位数字）</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.application.payment.PaymentPasswordService#setPaymentPassword
 */
public record SetPaymentPasswordRequest(
        /**
         * 支付密码（必填，6 位数字）。
         * <p>独立于登录密码，用于资金操作的身份验证。
         * 存储时使用 BCrypt 强哈希，不存储明文。</p>
         */
        String paymentPassword
) {
}
