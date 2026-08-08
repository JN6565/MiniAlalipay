package com.minialalipay.user.interfaces.dto.internal;

/**
 * 内部身份校验响应 DTO。
 *
 * @param matched 三要素是否全部匹配
 * @param identityBound 用户是否已绑定身份信息；无论匹配与否都返回，
 *                      供调用方区分「未绑定身份」与「已绑定但不匹配」两类失败
 * @param realName 持卡人姓名；仅 matched=true 时回填请求值，避免泄露用户存储信息
 * @param phone 手机号；仅 matched=true 时回填请求值，避免泄露用户存储信息
 */
public record VerifyIdentityResponse(
        boolean matched,
        boolean identityBound,
        String realName,
        String phone
) {
}
