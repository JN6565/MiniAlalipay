package com.minialalipay.ai.application.port;

import java.util.List;

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
}
