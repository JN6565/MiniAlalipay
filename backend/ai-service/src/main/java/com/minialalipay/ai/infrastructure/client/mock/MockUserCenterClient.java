package com.minialalipay.ai.infrastructure.client.mock;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 用户中心 Mock 客户端。
 *
 * <p>阶段四使用 Mock 数据替代真实 HTTP 调用。
 * 待用户中心就绪后替换为真实 HTTP 客户端。</p>
 */
@Service
public class MockUserCenterClient {

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

    /**
     * 调用用户中心工具。
     *
     * @param toolName 工具名
     * @param params 参数
     * @return Mock 响应数据
     */
    public Map<String, Object> invoke(String toolName, Map<String, Object> params) {
        if ("search_payees".equals(toolName)) {
            return Map.of("users", MOCK_USERS);
        }
        return Map.of();
    }
}
