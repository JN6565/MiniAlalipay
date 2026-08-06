package com.minialalipay.ai.application.port;

import java.util.List;
import java.util.Map;

/**
 * 用户中心端口，定义 AI 服务与用户中心的调用契约。
 *
 * <p>实现类负责 HTTP 调用、超时、重试和错误映射，调用方不关心底层细节。
 * 当前实现：Mock 客户端（阶段四），真实 HTTP 客户端（阶段五）。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>只能通过网关或已批准的公开契约调用，不直连数据库</li>
 *   <li>返回的用户信息必须脱敏（只返回脱敏后字段）</li>
 *   <li>调用方根据登录态派生主体身份，不信任参数中的 userId</li>
 * </ul>
 */
public interface UserCenterPort {

    /**
     * 模糊搜索候选收款人。
     *
     * @param userId 当前用户 ID（由服务端派生）
     * @param query 搜索关键词（昵称或手机号尾号），1-64 字符
     * @param limit 返回上限，默认 10，最大 20
     * @return 搜索结果，含脱敏后的用户列表
     */
    List<Map<String, Object>> searchPayees(String userId, String query, int limit);
}
