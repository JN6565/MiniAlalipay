package com.minialalipay.ai.interfaces.web.dto;

/**
 * 会话摘要响应 DTO，用于会话列表展示。
 *
 * @param sessionId 会话 ID
 * @param title 会话标题（取首条用户消息摘要，无历史消息时返回"新会话"）
 * @param lastActiveAt 最后活跃时间（ISO-8601 格式字符串）
 * @param messageCount 已保存的消息条数
 * @param status 会话状态（ACTIVE、CLOSED、EXPIRED 等）
 */
public record SessionSummaryResponse(
        String sessionId,
        String title,
        String lastActiveAt,
        int messageCount,
        String status
) {
}
