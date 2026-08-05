package com.minialalipay.ai.application.port;

import com.minialalipay.ai.domain.agent.IntentType;

import java.util.Map;

/**
 * 语言模型返回的结构化响应。
 *
 * <p>原始 LLM 输出经 JSON Schema 校验和意图解析后映射为本记录。
 * 槽位只用于草稿编排，不替代业务库中的确定性数据。</p>
 *
 * @param content 自然语言回复正文（面向用户展示）
 * @param intent 识别出的意图类型，低置信度时为 UNKNOWN
 * @param slots 从用户输入中提取的结构化槽位（如 payeeId、amountFen）
 * @param tokenCount 模型生成消耗的 Token 估算数
 * @param lowConfidence 意图置信度不足，需展示支持范围引导用户澄清
 */
public record ChatResponse(
        String content,
        IntentType intent,
        Map<String, Object> slots,
        int tokenCount,
        boolean lowConfidence
) {
}
