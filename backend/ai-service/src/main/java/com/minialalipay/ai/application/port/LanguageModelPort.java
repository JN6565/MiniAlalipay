package com.minialalipay.ai.application.port;

import com.minialalipay.ai.domain.tool.ToolCatalog;

import java.util.List;
import java.util.function.Consumer;

/**
 * 语言模型端口，定义 AI 服务与 LLM 之间的契约。
 *
 * <p>实现类负责适配不同 LLM 提供方（OpenAI 兼容、自有部署等），
 * 并在不可用时提供降级话术。调用方不关心底层实现细节。</p>
 *
 * <h3>约定</h3>
 * <ul>
 *   <li>实现必须支持超时、熔断和并发限制</li>
 *   <li>返回的 ChatResponse 不得包含支付密码、令牌或确认上下文</li>
 *   <li>槽位值必须经结构化输出校验</li>
 * </ul>
 */
public interface LanguageModelPort {

    /**
     * 向语言模型发送对话请求。
     *
     * @param systemPrompt 系统提示词，定义本次对话的角色、工具和安全约束
     * @param history 近期对话历史（最近 N 轮），自动截断到模型上下文窗口
     * @param userMessage 当前用户输入（已脱敏）
     * @return 结构化的模型响应，含意图、槽位和自然语言回复
     */
    ChatResponse chat(String systemPrompt, List<ChatMessage> history, String userMessage);

    /**
     * 向语言模型发送对话请求，并在生成过程中逐步回调文本增量。
     *
     * <p>默认实现回退到阻塞式 {@link #chat}，完成后一次性回调全部内容。
     * 真实 LLM 适配器应覆盖此方法，使用 LLM 流式 API 逐 Token 回调，
     * 使前端 SSE 能实现“边生成边推送”的打字效果。</p>
     *
     * @param systemPrompt 系统提示词
     * @param history 对话历史
     * @param userMessage 用户输入
     * @param onContentDelta 文本增量回调，每收到一个 Token 片段即调用
     * @return 结构化的模型响应（与 {@link #chat} 返回格式相同）
     */
    default ChatResponse streamChat(String systemPrompt, List<ChatMessage> history,
                                    String userMessage, Consumer<String> onContentDelta) {
        ChatResponse response = chat(systemPrompt, history, userMessage);
        if (response.content() != null) {
            onContentDelta.accept(response.content());
        }
        return response;
    }

    /**
     * 纯自然语言流式调用，不附加结构化输出格式指令。
     *
     * <p>用于工具执行后的第二次 LLM 调用：此时意图已由第一次调用确定，
     * 只需让 LLM 基于工具结果生成自然语言回复，不再要求 JSON 格式输出。
     * 避免结构化 JSON 被直接流式推送到前端。</p>
     *
     * @param systemPrompt 系统提示词
     * @param history 对话历史
     * @param userMessage 用户指令（通常包含工具结果上下文）
     * @param onContentDelta 文本增量回调
     * @return 响应（content 为自然语言文本，非 JSON）
     */
    default ChatResponse streamNaturalLanguageChat(
            String systemPrompt, List<ChatMessage> history,
            String userMessage, Consumer<String> onContentDelta) {
        // 默认实现回退到 streamChat（兼容未覆盖的实现）
        return streamChat(systemPrompt, history, userMessage, onContentDelta);
    }

    /**
     * Agent 单步推理：给定完整对话上下文和可用工具定义，LLM 决定调用工具或给出最终回复。
     *
     * <p>用于 ReAct 主循环。真实模式通过 Spring AI function calling 实现；
     * Mock 模式通过关键词匹配返回模拟的工具调用决策。</p>
     *
     * @param messages 完整对话消息列表（含 system、user、assistant、tool results）
     * @param tools 当前可用工具定义（来自 ToolCatalog，排除 HIGH_RISK_WRITE）
     * @return {@link AgentDecision.ToolCall} 或 {@link AgentDecision.FinalReply}
     */
    default AgentDecision agentStep(List<ChatMessage> messages,
                                    List<ToolCatalog.ToolDefinition> tools) {
        // 默认实现：返回 FinalReply 降级话术（兼容未覆盖的实现）
        return new AgentDecision.FinalReply(
                "抱歉，AI 助手暂时不可用。您可以使用传统表单完成转账、查余额、还花呗等操作。",
                10);
    }

    /**
     * 基于真实工具结果的 Agent 单步推理。
     *
     * <p>在 AgentLoop 执行工具后调用，将真实工具结果以 ToolResponseMessage 形式传给 LLM，
     * 让 LLM 基于实际数据决定下一步操作（继续调用工具或生成最终回复）。</p>
     *
     * @param messages 当前消息列表（不含工具结果）
     * @param tools 可用工具定义
     * @param toolCallId 上一次工具调用的 ID
     * @param toolName 上一次工具调用的工具名
     * @param actualResult 工具执行的真实结果 JSON
     * @return {@link AgentDecision.ToolCall} 或 {@link AgentDecision.FinalReply}
     */
    default AgentDecision agentStepWithToolResult(
            List<ChatMessage> messages,
            List<ToolCatalog.ToolDefinition> tools,
            String toolCallId, String toolName, String actualResult) {
        // 默认实现：回退到普通 agentStep（兼容未覆盖的实现）
        return agentStep(messages, tools);
    }
}
