package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.AgentDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 多查询工具规划测试，验证一个用户消息中的独立查询不会遗漏。 */
class MultiQueryToolPlannerTest {

    private final MultiQueryToolPlanner planner = new MultiQueryToolPlanner();

    @Test
    void shouldPlanAllIndependentQueriesInUserOrder() {
        List<AgentDecision.ToolCall> calls = planner.plan("查花呗，查余额，查交易");

        assertThat(calls).extracting(AgentDecision.ToolCall::toolName)
                .containsExactly("get_credit_summary", "get_balance", "list_transactions");
    }

    @Test
    void shouldNotParallelizeMoneyMovementWithQueries() {
        assertThat(planner.plan("查余额后转给张三 100 元")).isEmpty();
        assertThat(planner.plan("查花呗后还款 100 元")).isEmpty();
    }

    @Test
    void shouldLeaveSingleQueryToNormalAgentLoop() {
        assertThat(planner.plan("查余额")).isEmpty();
    }
}
