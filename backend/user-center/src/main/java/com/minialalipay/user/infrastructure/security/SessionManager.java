package com.minialalipay.user.infrastructure.security;

import com.minialalipay.user.domain.user.SessionManagerPort;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理器。
 *
 * <p>使用内存存储会话，管理会话的创建、查询、验证和销毁。
 * 会话令牌通过 HttpOnly Cookie 传递，支持 CSRF 令牌校验。</p>
 *
 * <p>注意：当前为 MVP 阶段的内存实现，生产环境应替换为 Redis 实现。</p>
 *
 * <p>会话生命周期：
 * <ul>
 *   <li>登录成功后创建会话，生成唯一的会话令牌</li>
 *   <li>会话令牌存储在内存中，TTL 为 24 小时</li>
 *   <li>每次请求验证会话令牌是否有效（存在且未过期）</li>
 *   <li>退出登录时删除会话</li>
 *   <li>会话过期后自动删除，用户需要重新登录</li>
 * </ul>
 * </p>
 *
 * <p>安全规则：
 * <ul>
 *   <li>会话令牌使用 UUID 生成，保证唯一性和不可预测性</li>
 *   <li>会话令牌通过 HttpOnly Cookie 传递，防止 XSS 攻击窃取</li>
 *   <li>支持 CSRF 令牌校验，防止 CSRF 攻击</li>
 *   <li>会话数据存储在内存，不存储在客户端，防止篡改</li>
 * </ul>
 * </p>
 */
@Component
public class SessionManager implements SessionManagerPort {

    /**
     * 会话过期时间（24 小时，单位毫秒）。
     */
    private static final long SESSION_EXPIRE_MS = 24 * 60 * 60 * 1000;

    /**
     * 会话存储：token -> SessionEntry。
     */
    private final Map<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    /**
     * 定时清理过期会话的调度器。
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * 构造函数，启动定时清理任务。
     */
    public SessionManager() {
        // 每小时清理一次过期会话
        scheduler.scheduleAtFixedRate(this::cleanExpiredSessions, 1, 1, TimeUnit.HOURS);
    }

    /**
     * 创建新会话。
     *
     * <p>登录成功后调用，生成唯一的会话令牌，并将用户 ID 存储到内存。
     * 会话令牌使用 UUID 生成，保证唯一性和不可预测性。</p>
     *
     * @param userId 用户 ID
     * @return 会话令牌（UUID 格式）
     * @throws IllegalArgumentException 如果用户 ID 为空
     */
    @Override
    public String createSession(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }

        // 生成会话令牌
        String token = UUID.randomUUID().toString();

        // 存储到内存，记录创建时间
        sessions.put(token, new SessionEntry(userId, System.currentTimeMillis()));

        return token;
    }

    /**
     * 验证会话令牌是否有效。
     *
     * <p>每次请求调用，检查会话令牌是否存在于内存中。
     * 如果存在且未过期，则会话有效。</p>
     *
     * @param token 会话令牌
     * @return 用户 ID（如果会话有效）
     */
    @Override
    public String validateSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        SessionEntry entry = sessions.get(token);
        if (entry == null) {
            return null;
        }

        // 检查是否过期
        if (System.currentTimeMillis() - entry.createdAt() > SESSION_EXPIRE_MS) {
            sessions.remove(token);
            return null;
        }

        return entry.userId();
    }

    /**
     * 销毁会话。
     *
     * <p>退出登录时调用，从内存中删除会话。
     * 删除后会话令牌立即失效，用户需要重新登录。</p>
     *
     * @param token 会话令牌
     */
    @Override
    public void destroySession(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        sessions.remove(token);
    }

    @Override
    public void destroyAllSessions(String userId) {
        if (userId == null || userId.isBlank()) return;
        sessions.entrySet().removeIf(entry -> userId.equals(entry.getValue().userId()));
    }

    /**
     * 清理过期会话。
     */
    private void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > SESSION_EXPIRE_MS);
    }

    /**
     * 会话条目记录。
     *
     * @param userId    用户 ID
     * @param createdAt 创建时间（毫秒时间戳）
     */
    private record SessionEntry(String userId, long createdAt) {
    }
}
