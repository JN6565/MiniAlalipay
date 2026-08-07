package com.minialalipay.ai.interfaces.web.dto;

import java.time.Instant;

/**
 * 会话内消息摘要 DTO，用于历史消息列表展示。
 *
 * @param messageId 消息 ID
 * @param role 消息角色（USER / ASSISTANT）
 * @param content 脱敏后消息内容
 * @param createdAt 消息创建时间
 */
public record MessageSummaryResponse(
        String messageId,
        String role,
        String content,
        Instant createdAt
) {
}
