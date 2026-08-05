package com.minialalipay.ai.application.port;

import com.minialalipay.ai.domain.agent.MessageRole;

/**
 * LLM 对话消息，用于构建发送给语言模型的上下文。
 *
 * @param role 消息角色（USER/ASSISTANT/SYSTEM）
 * @param content 脱敏后的消息正文
 */
public record ChatMessage(MessageRole role, String content) {
}
