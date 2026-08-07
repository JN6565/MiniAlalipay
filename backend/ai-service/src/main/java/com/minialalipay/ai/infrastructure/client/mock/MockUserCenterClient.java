package com.minialalipay.ai.infrastructure.client.mock;

import com.minialalipay.ai.application.port.UserCenterPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 用户中心 Mock 客户端（开发/测试用）。
 *
 * <p>当未配置真实 API Key 或显式启用 Mock 模式时使用。
 * 通过 {@code ai.client.mock-mode} 属性控制。</p>
 */
@Service
@ConditionalOnProperty(name = "ai.client.mock-mode", havingValue = "true", matchIfMissing = false)
public class MockUserCenterClient implements UserCenterPort {

    private static final List<Map<String, Object>> MOCK_USERS = List.of(
            Map.of("userId", "01J5Q000000000000000000010",
                    "nickname", "张三",
                    "phoneTail", "5678"),
            Map.of("userId", "01J5Q000000000000000000011",
                    "nickname", "李四",
                    "phoneTail", "1234"),
            Map.of("userId", "01J5Q000000000000000000012",
                    "nickname", "王五",
                    "phoneTail", "9012"),
            Map.of("userId", "01J5Q000000000000000000013",
                    "nickname", "老王",
                    "phoneTail", "3456")
    );

    @Override
    public List<Map<String, Object>> searchPayees(String userId, String query, int limit) {
        // 空 query 返回空列表，与真实 API 的 minLength:1 约束保持一致
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // Mock 实现：根据 query 过滤昵称或手机号尾号，按 limit 截断
        String lowerQuery = query.toLowerCase();
        List<Map<String, Object>> filtered = MOCK_USERS.stream()
                .filter(user -> {
                    String nickname = (String) user.get("nickname");
                    String phoneTail = (String) user.get("phoneTail");
                    return (nickname != null && nickname.toLowerCase().contains(lowerQuery))
                            || (phoneTail != null && phoneTail.contains(query));
                })
                .limit(Math.max(limit, 0))
                .toList();
        return filtered;
    }
}
