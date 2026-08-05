package com.minialalipay.ai.domain.agent;

/**
 * AI Agent 会话生命周期状态。
 *
 * <p>状态流转规则：
 * <ul>
 *   <li>新建会话 → {@link #ACTIVE}（初始状态，非终态）</li>
 *   <li>{@link #ACTIVE} → {@link #CLOSED}（用户主动关闭，终态，不可恢复）</li>
 *   <li>{@link #ACTIVE} → {@link #EXPIRED}（超时失效，终态，不可恢复）</li>
 * </ul>
 *
 * <p>只有 ACTIVE 状态的会话可以接收新消息和工具调用。</p>
 */
public enum AgentSessionStatus {
    /** 会话有效，可以继续接收消息并组装脱敏上下文。非终态。 */
    ACTIVE,

    /** 会话已由用户或系统正常关闭，不再接收新消息。终态，不可恢复。 */
    CLOSED,

    /** 会话因超过有效期失效，未提交草稿也应按规则过期。终态，不可恢复。 */
    EXPIRED
}
