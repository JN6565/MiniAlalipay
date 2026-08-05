package com.minialalipay.ai.interfaces.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 消息发送请求 DTO。
 *
 * <p>客户端通过此结构向 AI Talk 接口提交自然语言消息。
 * sessionId 首次对话可空，服务端自动创建新会话。</p>
 *
 * @param clientMessageId 客户端消息幂等键，长度 16-64
 * @param sessionId 会话 ID（ULID），首次对话可空
 * @param content 用户输入的自然语言消息，1-2000 字符
 */
public record SendMessageRequest(
        @NotBlank(message = "客户端消息 ID 不能为空")
        @Size(min = 16, max = 64, message = "客户端消息 ID 长度必须在 16 到 64 之间")
        String clientMessageId,

        @Size(max = 26, message = "会话 ID 长度不能超过 26")
        String sessionId,

        @NotBlank(message = "消息内容不能为空")
        @Size(min = 1, max = 2000, message = "消息内容不能超过 2000 个字符")
        String content
) {
}
