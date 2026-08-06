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
@ConditionalOnProperty(name = "ai.client.mock-mode", havingValue = "true", matchIfMissing = true)
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
                    "phoneTail", "9012")
    );

    @Override
    public List<Map<String, Object>> searchPayees(String userId, String query, int limit) {
        return MOCK_USERS;
    }

    /**
     * 调用用户中心工具（兼容旧 ToolRouter 的调度模式）。
     *
     * @param toolName 工具名
     * @param params 参数
     * @return Mock 响应数据
     */
    public Map<String, Object> invoke(String toolName, Map<String, Object> params) {
        if ("search_payees".equals(toolName)) {
            return Map.of("users", searchPayees("", "", 10));
        }
        return Map.of();
    }
}
