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
 *   <li>{@code kind} 为 {@link MessageKind#TOOL_RESULT} 时，{@code toolName} 必须有值</li>
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
    /** 消息类型：TEXT（文本回复）或 TOOL_RESULT（工具结果），默认 TEXT */
    private final MessageKind kind;
    /** 工具名称，仅 TOOL_RESULT 类型有值 */
    private final String toolName;

    /**
     * 创建新消息（向后兼容，默认 kind=TEXT）。
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
        this(messageId, sessionId, clientMessageId, role, contentRedacted,
                tokenCount, createdAt, MessageKind.TEXT, null);
    }

    /**
     * 创建新消息（完整构造，支持工具结果类型）。
     *
     * @param messageId 消息 ID（ULID）
     * @param sessionId 所属会话 ID
     * @param clientMessageId 客户端消息幂等键
     * @param role 消息角色
     * @param contentRedacted 脱敏后内容（TOOL_RESULT 时为 JSON 格式的工具摘要）
     * @param tokenCount Token 估算数
     * @param createdAt 创建时间
     * @param kind 消息类型
     * @param toolName 工具名称（TOOL_RESULT 时必填）
     */
    public AgentMessage(
            String messageId, String sessionId, String clientMessageId,
            MessageRole role, String contentRedacted, int tokenCount, Instant createdAt,
            MessageKind kind, String toolName
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
        this.kind = Objects.requireNonNull(kind, "消息类型不能为空");
        if (kind == MessageKind.TOOL_RESULT && (toolName == null || toolName.isBlank())) {
            throw new IllegalArgumentException("TOOL_RESULT 类型消息必须指定工具名称");
        }
        this.toolName = toolName;
    }

    // ---- getters ----

    public String getMessageId() { return messageId; }
    public String getSessionId() { return sessionId; }
    public String getClientMessageId() { return clientMessageId; }
    public MessageRole getRole() { return role; }
    public String getContentRedacted() { return contentRedacted; }
    public int getTokenCount() { return tokenCount; }
    public Instant getCreatedAt() { return createdAt; }
    public MessageKind getKind() { return kind; }
    public String getToolName() { return toolName; }
}
