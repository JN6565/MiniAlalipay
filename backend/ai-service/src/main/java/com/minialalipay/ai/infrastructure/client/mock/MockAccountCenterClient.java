package com.minialalipay.ai.infrastructure.client.mock;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 账户中心 Mock 客户端。
 *
 * <p>阶段四使用 Mock 数据替代真实 HTTP 调用。
 * 待账户中心就绪后替换为真实 HTTP 客户端。</p>
 */
@Service
public class MockAccountCenterClient {

    /**
     * 调用账户中心工具。
     *
     * @param toolName 工具名
     * @param params 参数
     * @return Mock 响应数据（金额统一使用分）
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
