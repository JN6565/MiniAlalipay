package com.minialalipay.user.interfaces.dto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 绑定身份请求 DTO。
 *
 * @param realName 真实姓名，2-32 个字符
 * @param idCard 身份证号，18 位，末位可为 X/x
 */
public record BindIdentityRequest(
        @NotBlank(message = "真实姓名不能为空")
        @Size(min = 2, max = 32, message = "真实姓名长度必须在 2-32 位之间")
        String realName,

        @NotBlank(message = "身份证号不能为空")
        @Pattern(regexp = "^\\d{17}[\\dXx]$", message = "身份证号格式不正确")
        String idCard
) {
}
