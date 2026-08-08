package com.minialalipay.ai.domain.agent;

/**
 * AI 对话消息类型枚举。
 *
 * <p>区分同一会话中不同用途的消息，前端恢复历史视图时根据类型决定渲染方式：
 * <ul>
 *   <li>{@link #TEXT}：文本回复，前端渲染为普通气泡</li>
 *   <li>{@link #TOOL_RESULT}：工具执行结果，前端渲染为内嵌卡片（余额、额度、交易记录等）</li>
 * </ul>
 * </p>
 */
public enum MessageKind {
    /** 文本回复，包括用户消息和 AI 文本回复。 */
    TEXT,

    /** 工具执行结果，content_redacted 存储 JSON 格式的工具摘要和数据。 */
    TOOL_RESULT
}
