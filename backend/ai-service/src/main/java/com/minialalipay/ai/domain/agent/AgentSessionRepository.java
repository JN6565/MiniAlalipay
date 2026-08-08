package com.minialalipay.ai.domain.agent;

import java.util.List;
import java.util.Optional;

/**
 * AI Agent 会话仓储接口。
 *
 * <p>定义会话聚合根的持久化操作契约，实现类在 infrastructure 层。
 * 所有查询返回领域对象或明确投影，不暴露 PO。</p>
 */
public interface AgentSessionRepository {

    /**
     * 根据会话 ID 查找会话。
     *
     * @param sessionId 会话 ID
     * @return 会话聚合根，不存在时返回 {@link Optional#empty()}
     */
    Optional<AgentSession> findById(String sessionId);

    /**
     * 查询用户的活跃会话列表，按最后活跃时间倒序。
     *
     * @param userId 用户 ID
     * @return 活跃会话列表
     */
    List<AgentSession> findActiveByUserId(String userId);

    /**
     * 保存会话聚合根。
     * <p>新会话执行 INSERT，已有会话通过版本 CAS 执行 UPDATE。
     * 版本冲突时抛出 {@link IllegalStateException}。</p>
     *
     * @param session 会话聚合根
     */
    void save(AgentSession session);

    /**
     * 软删除会话：将状态设为 CLOSED。
     *
     * <p>软删除而非物理删除，保留审计日志完整性。
     * 已关闭的会话不再出现在活跃列表中。</p>
     *
     * @param sessionId 会话 ID
     * @return 是否成功关闭（会话不存在或已关闭时返回 false）
     */
    boolean closeSession(String sessionId);

    /**
     * 更新会话标题（用户自定义名称）。
     *
     * @param sessionId 会话 ID
     * @param title 新标题，最大 100 字符，空字符串表示清除自定义标题
     */
    void updateTitle(String sessionId, String title);

    /**
     * 重新激活已过期的会话，不受 status = 'ACTIVE' 条件限制。
     *
     * <p>当会话已被持久化为 EXPIRED 状态时，普通 CAS 更新无法匹配，
     * 需使用此方法强制恢复为 ACTIVE。</p>
     *
     * @param session 已调用 reactivate() 的会话聚合根
     */
    void reactivateSession(AgentSession session);
}
