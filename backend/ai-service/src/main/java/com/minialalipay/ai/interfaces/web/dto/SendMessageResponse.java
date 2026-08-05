package com.minialalipay.ai.interfaces.web.dto;

import java.util.Map;

/**
 * AI 消息响应 DTO，对应 API 信封的 data 字段。
 *
 * @param sessionId 当前会话 ID，后续消息需携带此 ID
 * @param messageId 本条 AI 回复的消息 ID
 * @param content AI 的自然语言回复正文
 * @param intent 识别出的意图类型（IntentType.name()）
 * @param slots 当前会话的结构化槽位
 * @param clarificationNeeded 是否需要用户进一步澄清
 */
public record SendMessageResponse(
        String sessionId,
        String messageId,
        String content,
        String intent,
        Map<String, Object> slots,
        boolean clarificationNeeded
) {
}
