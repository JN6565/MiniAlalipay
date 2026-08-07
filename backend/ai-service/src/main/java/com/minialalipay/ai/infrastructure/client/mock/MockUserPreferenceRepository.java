package com.minialalipay.ai.infrastructure.client.mock;

import com.minialalipay.ai.domain.agent.UserPreference;
import com.minialalipay.ai.domain.agent.UserPreferenceRepository;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户偏好的内存仓储实现（Mock 模式）。
 *
 * <p>使用 ConcurrentHashMap 存储偏好数据，服务重启后数据丢失。
 * 仅在 Mock 模式下使用，真实模式由 MyBatis Mapper 实现。</p>
 */
@Component
public class MockUserPreferenceRepository implements UserPreferenceRepository {

    /** key: userId + ":" + preferenceType */
    private final Map<String, UserPreference> store = new ConcurrentHashMap<>();

    @Override
    public Optional<UserPreference> findActive(String userId, String preferenceType) {
        String key = userId + ":" + preferenceType;
        UserPreference pref = store.get(key);
        if (pref != null && UserPreference.STATUS_ACTIVE.equals(pref.status())) {
            return Optional.of(pref);
        }
        return Optional.empty();
    }

    @Override
    public void saveOrUpdate(UserPreference preference) {
        String key = preference.userId() + ":" + preference.preferenceType();
        store.put(key, preference);
    }

    @Override
    public void revoke(String userId, String preferenceType) {
        String key = userId + ":" + preferenceType;
        store.remove(key);
    }
}
