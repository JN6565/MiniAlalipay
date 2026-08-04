package com.minialalipay.ai.infrastructure.persistence.po;

import java.time.Instant;

/**
 * AI 会话持久化对象，对应 {@code agent_db.agent_session} 表。
 *
 * <p>该表记录 AI 对话会话的脱敏摘要、结构化槽位和生命周期状态。
 * 槽位 JSON 仅用于草稿编排，不可替代业务库中的金额、账户或交易事实。</p>
 */
public class AgentSessionPO {

    /** 会话 ID，对应 CHAR(26) */
    private String sessionId;

    /** 用户 ID，对应 CHAR(26)，跨库逻辑引用 */
    private String userId;

    /** 脱敏摘要，对应 TEXT */
    private String summary;

    /** 结构化槽位 JSON，对应 JSON */
    private String slotsJson;

    /** 会话状态，对应 VARCHAR(16) */
    private String status;

    /** 乐观锁版本号，对应 BIGINT UNSIGNED */
    private Long version;

    /** 最近活跃时间，对应 DATETIME(3) */
    private Instant lastActiveAt;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    public AgentSessionPO() {
    }

    public AgentSessionPO(String sessionId, String userId, String summary, String slotsJson,
                          String status, Long version, Instant lastActiveAt, Instant createdAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.summary = summary;
        this.slotsJson = slotsJson;
        this.status = status;
        this.version = version;
        this.lastActiveAt = lastActiveAt;
        this.createdAt = createdAt;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getSlotsJson() { return slotsJson; }
    public void setSlotsJson(String slotsJson) { this.slotsJson = slotsJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
