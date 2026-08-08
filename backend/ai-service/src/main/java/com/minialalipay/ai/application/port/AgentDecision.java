package com.minialalipay.ai.application.port;

import java.util.Map;

/**
 * Agent 单步决策结果。
 *
 * <p>在 ReAct 循环的每一步，LLM 做出以下两种决策之一：
 * <ul>
 *   <li>{@link ToolCall}：调用指定工具，参数由 LLM 生成</li>
 *   <li>{@link FinalReply}：给出最终回复，结束循环</li>
 * </ul>
 *
 * <p>sealed interface 确保决策类型穷举，编译器可检查 switch 完整性。</p>
 */
public sealed interface AgentDecision {

    /** 估算本次调用消耗的 Token 数。 */
    int estimatedTokens();

    /**
     * LLM 决定调用工具。
     *
     * @param toolName 工具契约名称，与 ToolCatalog 注册名一致
     * @param arguments 工具调用参数，由 LLM 根据 inputSchema 生成
     * @param estimatedTokens 本次推理消耗的 Token 估算
     * @param toolCallId LLM 返回的工具调用 ID（用于构建 ToolResponseMessage，Mock 模式可为 null）
     */
    record ToolCall(String toolName, Map<String, Object> arguments,
                    int estimatedTokens, String toolCallId) implements AgentDecision {
        /** 兼容构造（无 toolCallId），用于 Mock 模式和降级场景 */
        public ToolCall(String toolName, Map<String, Object> arguments, int estimatedTokens) {
            this(toolName, arguments, estimatedTokens, null);
        }
    }

    /**
     * LLM 决定给出最终回复。
     *
     * @param content 面向用户的自然语言回复
     * @param estimatedTokens 本次推理消耗的 Token 估算
     */
    record FinalReply(String content, int estimatedTokens) implements AgentDecision {}
}
