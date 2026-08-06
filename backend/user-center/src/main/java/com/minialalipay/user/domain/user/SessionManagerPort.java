package com.minialalipay.user.domain.user;

/**
 * 会话管理端口（接口）。
 *
 * <p>定义会话创建、验证和销毁的能力，由基础设施层实现。
 * 应用层通过此端口访问会话管理功能，不直接依赖基础设施层实现。</p>
 *
 * <p>职责边界：
 * <ul>
 *   <li>只负责会话的创建、验证和销毁</li>
 *   <li>不关心具体实现（Redis、JWT 等）</li>
 *   <li>不包含业务逻辑</li>
 * </ul>
 * </p>
 */
public interface SessionManagerPort {

    /**
     * 创建会话。
     *
     * @param userId 用户 ID
     * @return 会话令牌
     */
    String createSession(String userId);

    /**
     * 验证会话。
     *
     * @param token 会话令牌
     * @return 用户 ID（如果会话有效）
     */
    String validateSession(String token);

    /**
     * 销毁会话。
     *
     * @param token 会话令牌
     */
    void destroySession(String token);

    /** 销毁指定用户的全部设备会话。 */
    void destroyAllSessions(String userId);
}
