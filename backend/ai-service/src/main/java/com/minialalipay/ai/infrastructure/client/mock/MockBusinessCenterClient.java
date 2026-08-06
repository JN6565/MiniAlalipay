package com.minialalipay.ai.infrastructure.client.mock;

import com.minialalipay.ai.application.port.BusinessCenterPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 业务中心 Mock 客户端（开发/测试用）。
 *
 * <p>当未配置真实 API Key 或显式启用 Mock 模式时使用。</p>
 */
@Service
@ConditionalOnProperty(name = "ai.client.mock-mode", havingValue = "true", matchIfMissing = true)
public class MockBusinessCenterClient implements BusinessCenterPort {

    @Override
    public Map<String, Object> createTransferDraft(
            String userId, String payeeId, long amountFen,
            String remark, String idempotencyKey) {
        return Map.of("draftId", "01J5Q000000000000000000120", "version", 0L);
    }

    @Override
    public Map<String, Object> validateTransferDraft(
            String userId, String draftId, String idempotencyKey) {
        return Map.of("valid", true, "checks",
                Map.of("balanceCheck", "PASS", "limitCheck", "PASS", "riskCheck", "PASS"));
    }

    @Override
    public Map<String, Object> getTransferDraft(String userId, String draftId) {
        return Map.of("draftId", draftId, "payeeId", "01J5Q000000000000000000010",
                "amountFen", 50000L, "remark", "测试转账", "version", 0L);
    }

    @Override
    public Map<String, Object> getTransferStatus(String userId, String transactionId) {
        return Map.of("transactionId", transactionId, "status", "SUCCESS");
    }

    @Override
    public Map<String, Object> submitConfirmedTransfer(
            String userId, String draftId, String confirmationHandle, String idempotencyKey) {
        return Map.of("transactionId", "01J5Q000000000000000000130", "status", "PROCESSING");
    }

    @Override
    public Map<String, Object> prepareConfirmationCard(String userId, String draftId) {
        return Map.of("cardType", "TRANSFER_CONFIRMATION",
                "payeeNickname", "张三", "payeePhoneTail", "5678",
                "amountFen", 50000L, "fundingSource", "BALANCE");
    }

    /**
     * 调用业务中心工具（兼容旧 ToolRouter 的调度模式）。
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
