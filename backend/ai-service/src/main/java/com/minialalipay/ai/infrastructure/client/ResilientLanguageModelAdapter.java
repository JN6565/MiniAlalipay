package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.AgentDecision;
import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * 唯一可注入的 LLM 组合适配器。
 *
 * <p>根据配置在真实 LLM 和中文降级适配器之间选择：
 * 未配置 API Key 或启用 Mock 模式时使用降级适配器，
 * 不依赖 {@code @Primary} 掩盖装配错误。</p>
 */
@Service
public class ResilientLanguageModelAdapter implements LanguageModelPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientLanguageModelAdapter.class);

    private final OpenAiLanguageModelAdapter primary;
    private final FallbackLanguageModelAdapter fallback;
    private final boolean useFallback;

    public ResilientLanguageModelAdapter(
            OpenAiLanguageModelAdapter primary,
            FallbackLanguageModelAdapter fallback,
            @Value("${spring.ai.openai.api-key:}") String apiKey) {
        this.primary = primary;
        this.fallback = fallback;
        this.useFallback = apiKey == null || apiKey.isBlank();
        if (useFallback) {
            log.info("LLM 适配器：使用中文降级模式（apiKeySet={}）",
                    !apiKey.isBlank());
        }
    }

    @Override
    public ChatResponse chat(String systemPrompt, List<ChatMessage> history,
                             String userMessage) {
        if (useFallback) {
            return fallback.chat(systemPrompt, history, userMessage);
        }
        try {
            return primary.chat(systemPrompt, history, userMessage);
        } catch (Exception e) {
            log.warn("LLM 调用失败，降级到预设中文回复: {}", e.getMessage());
            return fallback.chat(systemPrompt, history, userMessage);
        }
    }

    @Override
    public ChatResponse streamChat(String systemPrompt, List<ChatMessage> history,
                                   String userMessage, Consumer<String> onContentDelta) {
        if (useFallback) {
            ChatResponse response = fallback.chat(systemPrompt, history, userMessage);
            if (response.content() != null) onContentDelta.accept(response.content());
            return response;
        }
        try {
            return primary.streamChat(systemPrompt, history, userMessage, onContentDelta);
        } catch (Exception e) {
            log.warn("LLM 流式调用失败，降级到预设中文回复: {}", e.getMessage());
            ChatResponse response = fallback.chat(systemPrompt, history, userMessage);
            if (response.content() != null) onContentDelta.accept(response.content());
            return response;
        }
    }

    @Override
    public ChatResponse streamNaturalLanguageChat(String systemPrompt, List<ChatMessage> history,
                                                  String userMessage, Consumer<String> onContentDelta) {
        if (useFallback) {
            ChatResponse response = fallback.chat(systemPrompt, history, userMessage);
            if (response.content() != null) onContentDelta.accept(response.content());
            return response;
        }
        try {
            return primary.streamNaturalLanguageChat(systemPrompt, history, userMessage, onContentDelta);
        } catch (Exception e) {
            log.warn("LLM 自然语言流式调用失败，降级到预设中文回复: {}", e.getMessage());
            ChatResponse response = fallback.chat(systemPrompt, history, userMessage);
            if (response.content() != null) onContentDelta.accept(response.content());
            return response;
        }
    }

    @Override
    public AgentDecision agentStep(List<ChatMessage> messages,
                                   List<ToolCatalog.ToolDefinition> tools) {
        if (useFallback) {
            return new AgentDecision.FinalReply(
                    FallbackLanguageModelAdapter.FALLBACK_MESSAGE, 10);
        }
        try {
            return primary.agentStep(messages, tools);
        } catch (Exception e) {
            log.warn("Agent step 失败，降级到预设中文回复: {}", e.getMessage());
            return new AgentDecision.FinalReply(
                    FallbackLanguageModelAdapter.FALLBACK_MESSAGE, 10);
        }
    }

    @Override
    public AgentDecision agentStepWithToolResult(
            List<ChatMessage> messages,
            List<ToolCatalog.ToolDefinition> tools,
            String toolCallId, String toolName, String actualResult) {
        if (useFallback) {
            return new AgentDecision.FinalReply(
                    FallbackLanguageModelAdapter.FALLBACK_MESSAGE, 10);
        }
        try {
            return primary.agentStepWithToolResult(messages, tools, toolCallId, toolName, actualResult);
        } catch (Exception e) {
            log.warn("Agent step(工具结果后) 失败，降级到预设中文回复: {}", e.getMessage());
            return new AgentDecision.FinalReply(
                    FallbackLanguageModelAdapter.FALLBACK_MESSAGE, 10);
        }
    }
}
