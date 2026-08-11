package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.AgentDecision;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 多查询工具规划器。
 *
 * <p>当一条消息明确包含两个及以上相互独立的只读查询时，规划器生成受控的
 * 工具批次。金额写入、还款和转账消息不进入此批次，避免把查询与资金流程
 * 混成一个不可审计的并行操作。</p>
 */
@Component
public class MultiQueryToolPlanner {

    private static final Map<String, List<String>> QUERY_KEYWORDS = Map.of(
            "get_balance", List.of("余额", "账户资金"),
            "get_credit_summary", List.of("花呗", "信用额度", "信用"),
            "list_credit_bills", List.of("花呗账单", "信用账单"),
            "list_transactions", List.of("交易", "交易记录", "交易明细", "流水"),
            "get_account_summary", List.of("账户摘要", "账户信息")
    );

    /**
     * 规划独立只读查询。
     *
     * @param userMessage 用户原始消息
     * @return 至少两个查询时返回按用户表达顺序排列的工具调用，否则返回空列表
     */
    public List<AgentDecision.ToolCall> plan(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return List.of();
        String lower = userMessage.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "转账", "转给", "汇款", "转钱", "还款", "还花呗")) {
            return List.of();
        }

        List<PlannedQuery> planned = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : QUERY_KEYWORDS.entrySet()) {
            int position = firstKeywordPosition(lower, entry.getValue());
            if (position < 0) continue;
            // “交易状态”需要交易 ID，不能被“交易”泛匹配为交易明细。
            if ("list_transactions".equals(entry.getKey())
                    && containsAny(lower, "交易状态", "转账状态")) {
                continue;
            }
            // “花呗账单”只应进入账单工具，不重复调用花呗额度工具。
            if ("get_credit_summary".equals(entry.getKey())
                    && containsAny(lower, "花呗账单", "信用账单")) {
                continue;
            }
            planned.add(new PlannedQuery(entry.getKey(), position, argumentsFor(entry.getKey())));
        }
        planned.sort(Comparator.comparingInt(PlannedQuery::position));
        if (planned.size() < 2) return List.of();
        return planned.stream()
                .map(query -> new AgentDecision.ToolCall(query.toolName(), query.arguments(), 0))
                .toList();
    }

    private Map<String, Object> argumentsFor(String toolName) {
        if ("list_transactions".equals(toolName) || "list_credit_bills".equals(toolName)) {
            return Map.of("limit", 10);
        }
        return Map.of();
    }

    private int firstKeywordPosition(String text, List<String> keywords) {
        return keywords.stream()
                .map(text::indexOf)
                .filter(position -> position >= 0)
                .min(Integer::compareTo)
                .orElse(-1);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private record PlannedQuery(String toolName, int position, Map<String, Object> arguments) {}
}
