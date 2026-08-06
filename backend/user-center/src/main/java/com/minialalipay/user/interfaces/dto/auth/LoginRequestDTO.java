package com.minialalipay.user.interfaces.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求 DTO（接口层）。
 *
 * <p>接口层的登录请求数据传输对象，用于 {@link com.minialalipay.user.interfaces.auth.AuthController#login} 方法。
 * DTO 对象只在接口层使用，用于接收前端请求参数。</p>
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code loginName} - 登录名（必填，用于查询用户）</li>
 *   <li>{@code loginPassword} - 登录密码（必填，用于验证身份）</li>
 * </ul>
 * </p>
 *
 * <p>校验规则：
 * <ul>
 *   <li>登录名不能为空</li>
 *   <li>密码不能为空</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.interfaces.auth.AuthController#login
 */
public record LoginRequestDTO(
        /**
         * 登录名（必填）。
         * <p>用于查询用户，登录时规范化处理（转小写、去空格）。</p>
         */
        @NotBlank(message = "手机号或账户号不能为空")
        @Size(min = 11, max = 20, message = "手机号或账户号格式不合法")
        String loginIdentifier,

        /**
         * 登录密码（必填）。
         * <p>用于验证身份，与存储的 BCrypt 哈希值比较。</p>
         */
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度必须在 8-32 位之间")
        String loginPassword
) {
}
