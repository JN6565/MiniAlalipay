package com.minialalipay.user.interfaces.dto.internal;

/**
 * 内部身份校验响应 DTO。
 *
 * @param matched 三要素是否全部匹配
 * @param realName 用户存储的真实姓名（无论是否匹配都返回，方便调用方调试）
 * @param phone 用户存储的手机号
 */
public record VerifyIdentityResponse(
        boolean matched,
        String realName,
        String phone
) {
}
