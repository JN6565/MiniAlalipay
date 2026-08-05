package com.minialalipay.ai.domain.agent;

import java.util.List;
import java.util.Optional;

/**
 * AI 消息仓储接口，定义消息实体的持久化契约。
 *
 * <p>消息是不可变实体，只提供 INSERT 和 SELECT 操作。
 * 同一 {@code (sessionId, clientMessageId, role)} 的唯一约束由数据库保证。</p>
 */
public interface AgentMessageRepository {

    /**
     * 按客户端消息 ID 查找消息（用于幂等检查）。
     *
     * @param sessionId 会话 ID
     * @param clientMessageId 客户端消息幂等键
     * @param role 消息角色
     * @return 已存在的消息，不存在时返回 {@link Optional#empty()}
     */
    Optional<AgentMessage> findByClientMessageId(String sessionId, String clientMessageId, MessageRole role);

    /**
     * 查询会话内的消息，按创建时间正序（用于恢复对话上下文）。
     *
     * <p>注意：此方法返回最早 N 条，不适合"最近 N 轮"场景。
     * 获取最近消息请使用 {@link #findRecentBySessionId}。</p>
     *
     * @param sessionId 会话 ID
     * @param limit 最大返回数
     * @return 消息列表（正序）
     */
    List<AgentMessage> findBySessionId(String sessionId, int limit);

    /**
     * 查询会话内最近 N 条消息，按创建时间正序返回（用于上下文窗口）。
     *
     * <p>先按 {@code created_at DESC, message_id DESC} 取最近窗口，
     * 再反转恢复正序。同毫秒消息使用 message_id 作为稳定排序键。</p>
     *
     * @param sessionId 会话 ID
     * @param limit 最大返回数
     * @return 最近消息列表（正序）
     */
    List<AgentMessage> findRecentBySessionId(String sessionId, int limit);

    /**
     * 新增消息。
     *
     * @param message 消息实体
     */
    void insert(AgentMessage message);
}
