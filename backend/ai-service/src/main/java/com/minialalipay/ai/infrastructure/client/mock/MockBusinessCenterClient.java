package com.minialalipay.ai.infrastructure.client.mock;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 业务中心 Mock 客户端。
 *
 * <p>阶段四使用 Mock 数据替代真实 HTTP 调用。
 * 待业务中心就绪后替换为真实 HTTP 客户端。</p>
 */
@Service
public class MockBusinessCenterClient {

    /**
     * 调用业务中心工具。
     *
     * @param toolName 工具名
     * @param params 参数
     * @return Mock 响应数据（金额统一使用分）
     */
    public Map<String, Object> invoke(String toolName, Map<String, Object> params) {
        return switch (toolName) {
            case "get_transaction_status" -> Map.of(
                    "transactionId", params.getOrDefault("transactionId", "unknown"),
                    "status", "SUCCESS"
            );
            case "create_transfer_draft" -> Map.of(
                    "draftId", "01J5Q000000000000000000120",
                    "version", 0L
            );
            case "validate_transfer_draft" -> Map.of(
                    "valid", true,
                    "checks", Map.of(
                            "balanceCheck", "PASS",
                            "limitCheck", "PASS",
                            "riskCheck", "PASS"
                    )
            );
            case "prepare_confirmation_card" -> Map.of(
                    "cardType", "TRANSFER_CONFIRMATION",
                    "payeeNickname", "张三",
                    "payeePhoneTail", "5678",
                    "amountFen", params.getOrDefault("amountFen", 0L),
                    "fundingSource", "BALANCE"
            );
            case "submit_confirmed_transfer" -> Map.of(
                    "transactionId", "01J5Q000000000000000000130",
                    "status", "PROCESSING"
            );
            default -> Map.of();
        };
    }
}
