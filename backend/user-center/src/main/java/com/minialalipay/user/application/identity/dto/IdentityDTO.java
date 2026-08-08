package com.minialalipay.user.application.identity.dto;

/**
 * 身份信息响应 DTO。
 *
 * <p>应用层身份绑定用例的返回结果，包含脱敏后的身份信息，
 * 不包含身份证号原值或哈希等敏感数据。接口层可直接透传。</p>
 *
 * @param realName       真实姓名
 * @param idCardMasked   身份证号掩码（前 4 位 + * + 后 4 位）
 * @param identityStatus 身份状态：PENDING_VERIFICATION / VERIFIED
 */
public record IdentityDTO(
        String realName,
        String idCardMasked,
        String identityStatus
) {
}
