package com.minialalipay.user.interfaces.dto.internal;

import jakarta.validation.constraints.NotBlank;

/**
 * 内部身份校验请求 DTO，供 account-center 调用。
 *
 * @param userId 用户 ID
 * @param holderName 持卡人姓名明文
 * @param idCard 身份证号明文
 * @param phone 手机号明文
 */
public record VerifyIdentityRequest(
        @NotBlank(message = "用户 ID 不能为空") String userId,
        @NotBlank(message = "持卡人姓名不能为空") String holderName,
        @NotBlank(message = "身份证号不能为空") String idCard,
        @NotBlank(message = "手机号不能为空") String phone
) {
}
