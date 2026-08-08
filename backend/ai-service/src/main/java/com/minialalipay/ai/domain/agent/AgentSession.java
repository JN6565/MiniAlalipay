package com.minialalipay.ai.domain.agent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AI Agent 会话聚合根。
 *
 * <p>管理一次用户与 AI 的完整对话生命周期，包括脱敏摘要、结构化槽位和状态流转。
 * 会话内的消息和工具调用通过 {@code session_id} 外键关联，不在这里保存集合引用。</p>
 *
 * <h3>关键不变量</h3>
 * <ul>
 *   <li>会话归属不可变：user_id 创建后不得修改</li>
 *   <li>槽位仅用于草稿编排，不能替代业务库中的金额、账户或交易状态</li>
 *   <li>摘要不得包含支付密码、确认令牌、访问令牌或原始敏感输入</li>
 *   <li>状态流转：ACTIVE → CLOSED（用户关闭）或 ACTIVE → EXPIRED（超时）</li>
 * </ul>
 *
 * <h3>状态说明</h3>
 * <ul>
 *   <li>{@link AgentSessionStatus#ACTIVE}：活跃，可接收消息和工具调用</li>
 *   <li>{@link AgentSessionStatus#CLOSED}：用户主动关闭，终态，不可恢复</li>
 *   <li>{@link AgentSessionStatus#EXPIRED}：超时失效，终态，不可恢复</li>
 * </ul>
 */
public class AgentSession {

    /** 默认会话超时时间（分钟）。 */
    public static final long DEFAULT_SESSION_TIMEOUT_MINUTES = 30;

    private final String sessionId;
    private final String userId;
    private String summary;
    private String title;
    private Map<String, Object> slots;
    private AgentSessionStatus status;
    private long version;
    private Instant lastActiveAt;
    private final Instant createdAt;

    /**
     * 创建新会话。
     *
     * @param sessionId 会话 ID（ULID）
     * @param userId 用户 ID（ULID）
     * @param now 当前时间
     */
    public AgentSession(String sessionId, String userId, Instant now) {
        this.sessionId = Objects.requireNonNull(sessionId, "会话 ID 不能为空");
        this.userId = Objects.requireNonNull(userId, "用户 ID 不能为空");
        this.summary = null;
        this.title = null;
        this.slots = new HashMap<>();
        this.status = AgentSessionStatus.ACTIVE;
        this.version = 0L;
        this.lastActiveAt = Objects.requireNonNull(now, "当前时间不能为空");
        this.createdAt = now;
    }

    /**
     * 从持久化重建会话。
     */
    public AgentSession(
            String sessionId, String userId, String summary, String title,
            Map<String, Object> slots, AgentSessionStatus status,
            long version, Instant lastActiveAt, Instant createdAt
    ) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.summary = summary;
        this.title = title;
        this.slots = slots != null ? new HashMap<>(slots) : new HashMap<>();
        this.status = status;
        this.version = version;
        this.lastActiveAt = lastActiveAt;
        this.createdAt = createdAt;
        validateInvariants();
    }

    /**
     * 更新会话活跃时间，表示接收到新消息或工具调用。
     *
     * @param now 当前时间
     */
    public void touch(Instant now) {
        assertActive();
        this.lastActiveAt = Objects.requireNonNull(now, "当前时间不能为空");
    }

    /**
     * 更新上下文压缩后的脱敏摘要。
     * 摘要不得包含支付密码、确认令牌或访问令牌。
     *
     * @param summary 脱敏摘要
     */
    public void updateSummary(String summary) {
        assertActive();
        this.summary = summary;
    }

    /**
     * 更新用户自定义会话标题。
     * 标题为空字符串时表示清除自定义标题，回退到首条消息摘要。
     *
     * @param title 新标题，最大 100 字符
     */
    public void updateTitle(String title) {
        this.title = (title != null && !title.isBlank()) ? title.trim() : null;
    }

    /**
     * 更新当前意图的结构化槽位。
     * 槽位只用于草稿编排，不能替代业务库的金额、账户或交易状态。
     *
     * @param slots 槽位 Map，不可变副本将被保存
     */
    public void updateSlots(Map<String, Object> slots) {
        assertActive();
        this.slots = slots != null ? new HashMap<>(slots) : new HashMap<>();
    }

    /**
     * 设置单个槽位值。
     *
     * @param key 槽位键
     * @param value 槽位值
     */
    public void setSlot(String key, Object value) {
        assertActive();
        this.slots.put(key, value);
    }

    /**
     * 重新激活已过期的会话。
     *
     * <p>当用户从历史会话继续对话时，将 EXPIRED 状态恢复为 ACTIVE，
     * 重置活跃时间并清除过期的草稿槽位。AI 上下文（摘要和消息历史）保留不变。</p>
     *
     * @param now 当前时间
     */
    public void reactivate(Instant now) {
        if (this.status != AgentSessionStatus.EXPIRED) {
            throw new IllegalStateException(
                    "仅 EXPIRED 状态的会话可重新激活，当前状态: " + this.status);
        }
        this.status = AgentSessionStatus.ACTIVE;
        this.lastActiveAt = Objects.requireNonNull(now, "当前时间不能为空");
        this.slots = new HashMap<>();
    }

    /**
     * 用户主动关闭会话。终态，不可恢复。
     */
    public void close() {
        if (this.status == AgentSessionStatus.CLOSED || this.status == AgentSessionStatus.EXPIRED) {
            throw new IllegalStateException("会话已处于终态，不可再次关闭");
        }
        this.status = AgentSessionStatus.CLOSED;
    }

    /**
     * 检查并处理会话超时。超时后将状态转为 EXPIRED。
     *
     * @param now 当前时间
     * @param timeoutMinutes 超时阈值（分钟）
     * @return 是否发生超时转换
     */
    public boolean checkExpiry(Instant now, long timeoutMinutes) {
        if (status != AgentSessionStatus.ACTIVE) {
            return false;
        }
        long elapsedMinutes = ChronoUnit.MINUTES.between(lastActiveAt, now);
        if (elapsedMinutes >= timeoutMinutes) {
            this.status = AgentSessionStatus.EXPIRED;
            return true;
        }
        return false;
    }

    /**
     * 使用默认超时 30 分钟检查过期。
     */
    public boolean checkExpiry(Instant now) {
        return checkExpiry(now, DEFAULT_SESSION_TIMEOUT_MINUTES);
    }

    /**
     * 判断会话是否仍可接收新消息。
     *
     * @return 仅 ACTIVE 状态返回 true
     */
    public boolean isActive() {
        return status == AgentSessionStatus.ACTIVE;
    }

    // ---- getters ----

    public String getSessionId() { return sessionId; }
    public String getUserId() { return userId; }
    public String getSummary() { return summary; }
    public String getTitle() { return title; }

    /** @return 槽位的不可变视图 */
    public Map<String, Object> getSlots() { return Collections.unmodifiableMap(slots); }
    public AgentSessionStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public Instant getCreatedAt() { return createdAt; }

    /**
     * 更新版本号，用于持久化层 CAS 更新成功后回写。
     *
     * @param version 新版本号
     */
    public void updateVersion(long version) {
        this.version = version;
    }

    private void assertActive() {
        if (status != AgentSessionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "会话状态为 " + status + "，不允许修改。仅 ACTIVE 会话可接受操作");
        }
    }

    private void validateInvariants() {
        Objects.requireNonNull(userId, "用户 ID 不能为空");
        Objects.requireNonNull(status, "会话状态不能为空");
        Objects.requireNonNull(lastActiveAt, "最后活跃时间不能为空");
        Objects.requireNonNull(createdAt, "创建时间不能为空");
        if (slots == null) {
            throw new IllegalStateException("槽位 Map 不能为 null");
        }
    }
}
