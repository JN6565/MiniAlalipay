package com.minialalipay.ai.domain.agent;

/**
 * AI 对话消息角色枚举。
 *
 * <p>每条消息的角色决定其在会话上下文中的位置和权限：
 * <ul>
 *   <li>{@link #USER}：终端用户发送的消息，可能包含敏感原始输入，保存前必须脱敏</li>
 *   <li>{@link #ASSISTANT}：AI 助手生成的结构化回复或自然语言解释，不包含资金确认信息</li>
 *   <li>{@link #SYSTEM}：系统注入的上下文、策略提示或工具结果，对用户不可见</li>
 * </ul>
 * </p>
 */
public enum MessageRole {
    /** 终端用户消息，保存前必须脱敏。 */
    USER,

    /** AI 助手回复，由模型生成并经过结构化输出校验。 */
    ASSISTANT,

    /** 系统注入的不可见上下文、工具调用结果或策略指令。 */
    SYSTEM
}
