package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.domain.agent.UserPreference;
import com.minialalipay.ai.domain.agent.UserPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * 用户偏好记忆服务。
 *
 * <p>在 AI Agent 交互过程中自动学习和回忆用户偏好，
 * 如常用收款人、默认转账金额等。偏好数据用于在后续交互中
 * 提供个性化建议，减少用户重复输入。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>偏好值不含支付密码、确认令牌等敏感信息</li>
 *   <li>偏好存储遵循用户授权版本（consent_version）</li>
 * </ul>
 */
@Service
public class UserPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceService.class);

    private final UserPreferenceRepository preferenceRepository;

    public UserPreferenceService(UserPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * 记忆最近使用的收款人信息。
     * 在转账成功后调用，将收款人 ID 和昵称保存为用户偏好。
     *
     * @param userId    用户 ID
     * @param payeeId   收款人用户 ID
     * @param nickname  收款人昵称
     * @param now       当前时间
     */
    public void rememberLastPayee(String userId, String payeeId, String nickname, Instant now) {
        if (payeeId == null || payeeId.isBlank()) return;
        String value = "{\"payeeId\":\"" + payeeId + "\",\"nickname\":\""
                + (nickname != null ? nickname.replace("\"", "\\\"") : "") + "\"}";
        savePreference(userId, UserPreference.TYPE_LAST_PAYEE, value, now);
        log.info("已记忆用户最近收款人: userId={}, payeeId={}", userId, payeeId);
    }

    /**
     * 获取用户最近使用的收款人。
     *
     * @param userId 用户 ID
     * @return 最近收款人信息（含 payeeId 和 nickname），不存在时返回空
     */
    public Optional<Map<String, String>> getLastPayee(String userId) {
        return preferenceRepository.findActive(userId, UserPreference.TYPE_LAST_PAYEE)
                .map(pref -> parseSimpleJson(pref.value()));
    }

    /**
     * 保存或更新用户偏好。
     */
    private void savePreference(String userId, String type, String value, Instant now) {
        Optional<UserPreference> existing = preferenceRepository.findActive(userId, type);
        if (existing.isPresent()) {
            UserPreference updated = new UserPreference(
                    existing.get().preferenceId(),
                    userId, type, value,
                    UserPreference.STATUS_ACTIVE,
                    existing.get().createdAt(),
                    now
            );
            preferenceRepository.saveOrUpdate(updated);
        } else {
            String id = AiServiceUtils.generateUlid();
            UserPreference newPref = new UserPreference(
                    id, userId, type, value,
                    UserPreference.STATUS_ACTIVE, now, now
            );
            preferenceRepository.saveOrUpdate(newPref);
        }
    }

    /**
     * 简单 JSON 解析（仅支持 {"key":"value"} 格式）。
     * 避免引入额外 JSON 库依赖。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> parseSimpleJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("偏好值 JSON 解析失败: {}", json);
            return Map.of();
        }
    }

}
