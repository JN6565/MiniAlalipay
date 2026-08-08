package com.minialalipay.user.interfaces.dto.identity;

/**
 * 身份信息响应 DTO。
 *
 * @param realName 真实姓名
 * @param idCardMasked 身份证号掩码
 * @param identityStatus 身份状态：PENDING_VERIFICATION / VERIFIED
 */
public record IdentityDTO(
        String realName,
        String idCardMasked,
        String identityStatus
) {
}
