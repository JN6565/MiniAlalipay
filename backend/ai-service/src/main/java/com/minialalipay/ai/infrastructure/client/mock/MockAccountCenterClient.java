package com.minialalipay.ai.infrastructure.client.mock;

import com.minialalipay.ai.application.port.AccountCenterPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 账户中心 Mock 客户端（开发/测试用）。
 *
 * <p>当未配置真实 API Key 或显式启用 Mock 模式时使用。</p>
 */
@Service
@ConditionalOnProperty(name = "ai.client.mock-mode", havingValue = "true", matchIfMissing = true)
public class MockAccountCenterClient implements AccountCenterPort {

    @Override
    public Map<String, Object> getAccountSummary(String userId) {
        return Map.of(
                "accountId", "01J5Q000000000000000000080",
                "availableFen", 1_000_000L,
                "frozenFen", 0L,
                "status", "ACTIVE"
        );
    }

    @Override
    public Map<String, Object> getBalance(String userId) {
        return Map.of("availableFen", 1_000_000L, "frozenFen", 0L);
    }

    @Override
    public Map<String, Object> listTransactions(String userId, int limit) {
        return Map.of("transactions", java.util.List.of(
                Map.of("transactionId", "01J5Q000000000000000000090",
                        "amountFen", 50000L, "status", "SUCCESS",
                        "occurredAt", "2026-08-04T06:00:00Z")
        ));
    }

    @Override
    public Map<String, Object> getCreditSummary(String userId) {
        return Map.of("totalLimitFen", 500_000L, "usedFen", 0L, "availableFen", 500_000L);
    }

    @Override
    public Map<String, Object> listCreditBills(String userId, int limit) {
        return Map.of("bills", java.util.List.of());
    }

    /**
     * 调用账户中心工具（兼容旧 ToolRouter 的调度模式）。
     */
    public Map<String, Object> invoke(String toolName, Map<String, Object> params) {
        return switch (toolName) {
            case "get_account_summary" -> Map.of(
                    "accountId", "01J5Q000000000000000000080",
                    "availableFen", 1_000_000L,
                    "frozenFen", 0L,
                    "status", "ACTIVE"
            );
            case "get_balance" -> Map.of(
                    "availableFen", 1_000_000L,
                    "frozenFen", 0L
            );
            case "list_transactions" -> Map.of(
                    "transactions", java.util.List.of(
                            Map.of("transactionId", "01J5Q000000000000000000090",
                                    "amountFen", 50000L,
                                    "status", "SUCCESS",
                                    "occurredAt", "2026-08-04T06:00:00Z")
                    )
            );
            case "get_credit_summary" -> Map.of(
                    "totalLimitFen", 500_000L,
                    "usedFen", 0L,
                    "availableFen", 500_000L
            );
            case "list_credit_bills" -> Map.of(
                    "bills", java.util.List.of()
            );
            case "create_credit_repayment_draft" -> Map.of(
                    "repaymentDraftId", "01J5Q000000000000000000100",
                    "version", 0L
            );
            case "submit_confirmed_credit_repayment" -> Map.of(
                    "transactionId", "01J5Q000000000000000000110",
                    "status", "PROCESSING"
            );
            default -> Map.of();
        };
    }
}
