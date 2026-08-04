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
}
