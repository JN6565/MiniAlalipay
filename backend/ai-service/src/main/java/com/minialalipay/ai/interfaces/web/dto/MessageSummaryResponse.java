package com.minialalipay.ai.interfaces.web.dto;

/**
 * 会话内消息摘要 DTO，用于历史消息列表展示。
 *
 * @param messageId 消息 ID
 * @param role 消息角色（USER / ASSISTANT）
 * @param content 脱敏后消息内容
 * @param createdAt 消息创建时间（ISO-8601 格式字符串）
 * @param kind 消息类型（TEXT / TOOL_RESULT），前端据此重建卡片
 * @param toolName 工具名称（仅 TOOL_RESULT 有值）
 */
public record MessageSummaryResponse(
        String messageId,
        String role,
        String content,
        String createdAt,
        String kind,
        String toolName
) {
}
