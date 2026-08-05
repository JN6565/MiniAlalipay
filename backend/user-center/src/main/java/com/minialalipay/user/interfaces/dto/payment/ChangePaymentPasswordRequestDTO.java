package com.minialalipay.user.interfaces.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改支付密码请求 DTO（接口层）。
 *
 * <p>接口层的修改支付密码请求数据传输对象，用于 PaymentPasswordController。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code currentPassword} - 当前支付密码（必填，用于验证身份）</li>
 *   <li>{@code newPassword} - 新支付密码（必填，6 位数字）</li>
 * </ul>
 * </p>
 *
 * <p>校验规则：
 * <ul>
 *   <li>当前支付密码不能为空</li>
 *   <li>新支付密码必须为 6 位数字</li>
 * </ul>
 * </p>
 */
public record ChangePaymentPasswordRequestDTO(
        /**
         * 当前支付密码（必填）。
         * <p>用于验证身份，确�是本人操作。</p>
         */
        @NotBlank(message = "当前支付密码不能为空")
        String currentPassword,

        /**
         * 新支付密码（必填，6 位数字）。
         * <p>独立于登录密码，用于资金操作的身份验证。
         * 存储时使用 BCrypt 强哈希，不存储明文。</p>
         */
        @NotBlank(message = "新支付密码不能为空")
        @Size(min = 6, max = 6, message = "新支付密码必须为 6 位")
        @Pattern(regexp = "^\\d{6}$", message = "新支付密码必须为 6 位数字")
        String newPassword
) {
}
