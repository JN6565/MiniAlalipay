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
 *   <li>{@code nickname} - 昵称（必填，2-20 位，用于展示和模糊搜索）</li>
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
        @NotBlank(message = "登录名不能为空")
        @Size(min = 4, max = 20, message = "登录名长度必须在 4-20 位之间")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "登录名只能包含字母、数字、下划线")
        String loginName,

        /**
         * 昵称（必填，2-20 位）。
         * <p>可重复的展示名称和模糊搜索条件，不要求唯一。</p>
         */
        @NotBlank(message = "昵称不能为空")
        @Size(min = 2, max = 20, message = "昵称长度必须在 2-20 位之间")
        String nickname,

        /**
         * 登录密码（必填，8-32 位）。
         * <p>至少包含一个大写字母、一个小写字母和一个数字。
         * 存储时使用 BCrypt 强哈希，不存储明文。</p>
         */
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度必须在 8-32 位之间")
        String loginPassword
) {
}
