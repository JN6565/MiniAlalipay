package com.minialalipay.user.interfaces.dto.user;

/**
 * 用户搜索结果 DTO。
 *
 * @param userId         用户 ID
 * @param accountNumber  系统账户号
 * @param nickname       昵称
 * @param identityStatus 身份状态
 * @param phoneTail      手机尾号（脱敏展示，4 位）
 */
public record UserSearchResultDTO(
        String userId,
        String accountNumber,
        String nickname,
        String identityStatus,
        String phoneTail
) {
}
