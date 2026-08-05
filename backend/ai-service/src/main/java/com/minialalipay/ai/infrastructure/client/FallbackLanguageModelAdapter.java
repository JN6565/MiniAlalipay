package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.domain.agent.IntentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * LLM 降级适配器。
 *
 * <p>作为独立的 Spring Bean 存在，始终返回预置中文降级话术。
 * 主适配器 {@link OpenAiLanguageModelAdapter} 已在内部实现 Mock 与真实 LLM 切换，
 * 本适配器提供额外的兜底路径。</p>
 */
@Component
public class FallbackLanguageModelAdapter {

    static final String FALLBACK_MESSAGE =
            "抱歉，AI 助手暂时不可用。您可以使用传统表单完成转账、查余额、还花呗等操作。";

    public ChatResponse chat(String systemPrompt, List<ChatMessage> history, String userMessage) {
        return new ChatResponse(
                FALLBACK_MESSAGE,
                IntentType.UNKNOWN,
                Map.of(),
                10,
                true
        );
    }
}
