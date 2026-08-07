package com.minialalipay.ai.domain.agent;

import java.util.Optional;

/**
 * 用户偏好仓储接口。
 *
 * <p>提供偏好的持久化操作，每个用户每种偏好类型只保留一条活跃记录。
 * 实现层负责加密存储敏感偏好值。</p>
 */
public interface UserPreferenceRepository {

    /**
     * 查询用户指定类型的活跃偏好。
     *
     * @param userId         用户 ID
     * @param preferenceType 偏好类型
     * @return 活跃偏好记录，不存在时返回空
     */
    Optional<UserPreference> findActive(String userId, String preferenceType);

    /**
     * 保存或更新用户偏好。
     * 当同类型偏好已存在时更新值，否则新建。
     *
     * @param preference 偏好记录
     */
    void saveOrUpdate(UserPreference preference);

    /**
     * 撤销用户指定类型的偏好。
     *
     * @param userId         用户 ID
     * @param preferenceType 偏好类型
     */
    void revoke(String userId, String preferenceType);
}
