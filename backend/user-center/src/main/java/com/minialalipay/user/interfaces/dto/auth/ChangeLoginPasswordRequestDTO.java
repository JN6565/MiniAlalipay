package com.minialalipay.user.interfaces.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** C 端修改登录密码请求，当前密码用于再次确认本人身份。 */
public record ChangeLoginPasswordRequestDTO(
        @NotBlank(message = "当前密码不能为空") String currentPassword,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 32, message = "新密码长度必须为 8-32 位") String newPassword
) {
}
