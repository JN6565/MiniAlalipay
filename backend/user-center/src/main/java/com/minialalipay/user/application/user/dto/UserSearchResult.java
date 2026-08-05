package com.minialalipay.user.application.user.dto;

/**
 * 用户搜索结果（应用层 DTO）。
 *
 * @param userId         用户 ID
 * @param loginName      登录名
 * @param nickname       昵称
 * @param identityStatus 身份状态
 */
public record UserSearchResult(
        String userId,
        String loginName,
        String nickname,
        String identityStatus
) {
}
