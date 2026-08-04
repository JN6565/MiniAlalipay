package com.minialalipay.ai.infrastructure.persistence.po;

import java.time.Instant;

/**
 * AI 消息持久化对象，对应 {@code agent_db.agent_message} 表。
 *
 * <p>该表保存用户和助手的脱敏消息，{@code (session_id, client_message_id, role)} 联合唯一
 * 保证同客户端消息同角色不会重复生成回复。</p>
 */
public class AgentMessagePO {

    /** 消息 ID，对应 CHAR(26) */
    private String messageId;

    /** 所属会话 ID，对应 CHAR(26) FK */
    private String sessionId;

    /** 客户端消息幂等键，对应 VARCHAR(64) */
    private String clientMessageId;

    /** 消息角色，对应 VARCHAR(16) */
    private String role;

    /** 脱敏内容，对应 TEXT */
    private String contentRedacted;

    /** Token 数，对应 INT UNSIGNED */
    private Integer tokenCount;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    public AgentMessagePO() {
    }

    public AgentMessagePO(String messageId, String sessionId, String clientMessageId,
                          String role, String contentRedacted, Integer tokenCount, Instant createdAt) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.clientMessageId = clientMessageId;
        this.role = role;
        this.contentRedacted = contentRedacted;
        this.tokenCount = tokenCount;
        this.createdAt = createdAt;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContentRedacted() { return contentRedacted; }
    public void setContentRedacted(String contentRedacted) { this.contentRedacted = contentRedacted; }
    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
