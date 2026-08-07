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
@ConditionalOnProperty(name = "ai.client.mock-mode", havingValue = "true", matchIfMissing = false)
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
        // 字段名与真实 API LedgerEntryPageDTO 对齐：items + nextCursor
        return Map.of("items", java.util.List.of(
                Map.of("entryId", 1L, "transactionId", "01J5Q000000000000000000090",
                        "direction", "OUT", "amountFen", 50000L,
                        "memo", "转账", "createdAt", "2026-08-04T06:00:00Z")
        ), "nextCursor", "");
    }

    @Override
    public Map<String, Object> listTransactions(String userId, int limit,
            String startTime, String endTime, String direction, String status) {
        // Mock 模式：返回包含多种方向的交易数据，便于前端测试筛选
        var allItems = java.util.List.of(
                Map.of("entryId", 1L, "transactionId", "01J5Q000000000000000000090",
                        "direction", "OUT", "amountFen", 50000L,
                        "memo", "转账", "createdAt", "2026-08-04T06:00:00Z"),
                Map.of("entryId", 2L, "transactionId", "01J5Q000000000000000000091",
                        "direction", "IN", "amountFen", 100000L,
                        "memo", "收款", "createdAt", "2026-08-03T10:00:00Z")
        );
        // 简单筛选：按方向过滤
        var filtered = allItems;
        if (direction != null && !direction.isBlank()) {
            filtered = allItems.stream()
                    .filter(item -> direction.equals(item.get("direction")))
                    .toList();
        }
        return Map.of("items", filtered, "nextCursor", "");
    }

    @Override
    public Map<String, Object> getCreditSummary(String userId) {
        return Map.of("totalLimitFen", 500_000L, "usedFen", 0L, "availableFen", 500_000L);
    }

    @Override
    public Map<String, Object> listCreditBills(String userId, int limit) {
        // 字段名与真实 API 信封解包后格式对齐：data 为 List
        return Map.of("data", java.util.List.of());
    }

    @Override
    public Map<String, Object> createCreditRepaymentDraft(
            String userId, long amountFen, String idempotencyKey) {
        return Map.of(
                "repaymentDraftId", "01J5Q000000000000000000100",
                "version", 0L,
                "amountFen", amountFen,
                "allocations", java.util.List.of(
                        Map.of("billId", "01J5Q000000000000000000101",
                                "amountFen", amountFen)
                )
        );
    }

    @Override
    public Map<String, Object> submitCreditRepayment(
            String userId, String repaymentDraftId,
            String paymentProofToken, String idempotencyKey) {
        return Map.of(
                "repaymentId", "01J5Q000000000000000000110",
                "status", "PROCESSING"
        );
    }
}
