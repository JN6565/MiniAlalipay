package com.minialalipay.ai.domain.agent;

/**
 * AI Agent会话生命周期状态。
 */
public enum AgentSessionStatus {
    /** 会话有效，可以继续接收消息并组装脱敏上下文。 */
    ACTIVE,

    /** 会话已由用户或系统正常关闭，不再接收新消息。 */
    CLOSED,

    /** 会话因超过有效期失效，未提交草稿也应按规则过期。 */
    EXPIRED
}
