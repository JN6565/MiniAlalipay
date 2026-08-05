package com.minialalipay.user.interfaces.dto.user;

/**
 * 用户搜索结果 DTO。
 *
 * @param userId         用户 ID
 * @param loginName      登录名
 * @param nickname       昵称
 * @param identityStatus 身份状态
 */
public record UserSearchResultDTO(
        String userId,
        String loginName,
        String nickname,
        String identityStatus
) {
}
