package com.minialalipay.user.application.payment.dto;

/**
 * 修改支付密码请求 DTO。
 *
 * <p>应用层的修改支付密码请求数据传输对象，用于 {@link com.minialalipay.user.application.payment.PaymentPasswordService#changePaymentPassword} 方法。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code currentPassword} - 当前支付密码（必填，用于验证身份）</li>
 *   <li>{@code newPassword} - 新支付密码（必填，6 位数字）</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.application.payment.PaymentPasswordService#changePaymentPassword
 */
public record ChangePaymentPasswordRequest(
        /**
         * 当前支付密码（必填）。
         * <p>用于验证身份，确�是本人操作。</p>
         */
        String currentPassword,

        /**
         * 新支付密码（必填，6 位数字）。
         * <p>独立于登录密码，用于资金操作的身份验证。
         * 存储时使用 BCrypt 强哈希，不存储明文。</p>
         */
        String newPassword
) {
}
