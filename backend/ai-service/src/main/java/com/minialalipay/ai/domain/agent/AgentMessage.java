package com.minialalipay.ai.domain.agent;

import java.time.Instant;
import java.util.Objects;

/**
 * AI 对话消息实体。
 *
 * <p>保存用户和助手的脱敏消息，用于恢复对话上下文和审计 AI 解释。
 * 每条消息属于一个会话，通过 {@code (session_id, client_message_id, role)} 联合唯一
 * 约束保证同一客户端消息不会重复生成同角色回复。</p>
 *
 * <h3>关键不变量</h3>
 * <ul>
 *   <li>{@code content_redacted} 不得包含支付密码、令牌、完整账号或未脱敏输入</li>
 *   <li>同一 {@code clientMessageId} 的同角色消息必须幂等，不可重复插入</li>
 *   <li>消息创建后不可修改，不设版本号</li>
 * </ul>
 */
public class AgentMessage {

    private final String messageId;
    private final String sessionId;
    private final String clientMessageId;
    private final MessageRole role;
    private final String contentRedacted;
    private final int tokenCount;
    private final Instant createdAt;

    /**
     * 创建新消息。
     *
     * @param messageId 消息 ID（ULID）
     * @param sessionId 所属会话 ID
     * @param clientMessageId 客户端消息幂等键
     * @param role 消息角色
     * @param contentRedacted 脱敏后内容
     * @param tokenCount Token 估算数
     * @param createdAt 创建时间
     */
    public AgentMessage(
            String messageId, String sessionId, String clientMessageId,
            MessageRole role, String contentRedacted, int tokenCount, Instant createdAt
    ) {
        this.messageId = Objects.requireNonNull(messageId, "消息 ID 不能为空");
        this.sessionId = Objects.requireNonNull(sessionId, "会话 ID 不能为空");
        this.clientMessageId = Objects.requireNonNull(clientMessageId, "客户端消息 ID 不能为空");
        this.role = Objects.requireNonNull(role, "消息角色不能为空");
        this.contentRedacted = Objects.requireNonNull(contentRedacted, "脱敏内容不能为空");
        if (tokenCount < 0) {
            throw new IllegalArgumentException("Token 数不能为负");
        }
        this.tokenCount = tokenCount;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    // ---- getters ----

    public String getMessageId() { return messageId; }
    public String getSessionId() { return sessionId; }
    public String getClientMessageId() { return clientMessageId; }
    public MessageRole getRole() { return role; }
    public String getContentRedacted() { return contentRedacted; }
    public int getTokenCount() { return tokenCount; }
    public Instant getCreatedAt() { return createdAt; }
}
