package com.minialalipay.user.interfaces.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求 DTO（接口层）。
 *
 * <p>接口层的注册请求数据传输对象，用于 {@link com.minialalipay.user.interfaces.auth.AuthController#register} 方法。
 * DTO 对象只在接口层使用，用于接收前端请求参数。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code loginName} - 登录名（必填，4-20 位，用于登录和唯一识别）</li>
 *   <li>{@code nickname} - 昵称（必填，2-20 位，仅用于展示）</li>
 *   <li>{@code loginPassword} - 登录密码（必填，8-32 位，至少包含大小写字母和数字）</li>
 * </ul>
 * </p>
 *
 * <p>校验规则：
 * <ul>
 *   <li>登录名长度 4-20 位，只能包含字母、数字、下划线</li>
 *   <li>昵称长度 2-20 位，不能包含特殊字符</li>
 *   <li>密码长度 8-32 位，至少包含一个大写字母、一个小写字母和一个数字</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.interfaces.auth.AuthController#register
 */
public record RegisterRequestDTO(
        /**
         * 登录名（必填，4-20 位）。
         * <p>用于登录和唯一识别，系统内唯一。
         * 注册时规范化处理（转小写、去空格）。</p>
         */
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phoneNumber,

        /**
         * 昵称（必填，2-20 位）。
         * <p>可重复的展示名称，不要求唯一。</p>
         */
        @Size(max = 20, message = "昵称长度不能超过 20 位")
        String nickname,

        /**
         * 登录密码（必填，8-32 位）。
         * <p>至少包含一个大写字母、一个小写字母和一个数字。
         * 存储时使用 BCrypt 强哈希，不存储明文。</p>
         */
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度必须在 8-32 位之间")
        String loginPassword,

        /** 支付密码，仅允许 6 位数字。 */
        @NotBlank(message = "支付密码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "支付密码必须为 6 位数字")
        String paymentPassword
) {
}
