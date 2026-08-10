package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.application.port.ToolResult;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.ToolAuditService;
import com.minialalipay.ai.domain.agent.AgentSession;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.ai.domain.tool.ToolRiskLevel;
import com.minialalipay.ai.infrastructure.client.ToolRouter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 多查询 Agent 编排测试，验证工具并发执行和基于事实的确定性回复。 */
class AgentLoopMultiQueryTest {

    @Test
    void shouldExecuteAllIndependentQueriesConcurrentlyWithoutModelRewrite() throws Exception {
        LanguageModelPort languageModel = mock(LanguageModelPort.class);
        ToolRouter toolRouter = mock(ToolRouter.class);
        ToolPolicyService toolPolicy = mock(ToolPolicyService.class);
        ToolAuditService toolAudit = mock(ToolAuditService.class);
        InjectionDetector injectionDetector = mock(InjectionDetector.class);
        ToolCatalog toolCatalog = new ToolCatalog();

        when(toolPolicy.evaluate(anyString(), any(AgentSession.class)))
                .thenReturn(new ToolPolicyService.PolicyDecision(
                        true, ToolRiskLevel.READ_ONLY, null, false));
        when(injectionDetector.check(anyString()))
                .thenReturn(InjectionDetector.InjectionCheckResult.SAFE);

        CountDownLatch allStarted = new CountDownLatch(3);
        when(toolRouter.route(anyString(), anyMap(), anyString())).thenAnswer(invocation -> {
            String toolName = invocation.getArgument(0);
            allStarted.countDown();
            if (!allStarted.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("只读工具未并发启动");
            }
            return switch (toolName) {
                case "get_credit_summary" -> new ToolResult("SUCCESS",
                        Map.of("totalLimitFen", 500_000L, "usedFen", 10_000L,
                                "availableFen", 490_000L), null, 10);
                case "get_balance" -> new ToolResult("SUCCESS",
                        Map.of("availableFen", 88_800L, "frozenFen", 0L), null, 10);
                case "list_transactions" -> new ToolResult("SUCCESS",
                        Map.of("items", List.of(Map.of("transactionId", "txn-1"))), null, 10);
                default -> throw new IllegalArgumentException("未预期工具: " + toolName);
            };
        });

        AgentLoop agentLoop = new AgentLoop(
                languageModel, toolCatalog, toolRouter, toolPolicy, toolAudit,
                injectionDetector, new ResultInterpreter(), new MultiQueryToolPlanner());
        AgentSession session = new AgentSession("session-1", "user-1", Instant.now());

        AgentLoop.AgentResult result = agentLoop.execute(new AgentLoop.AgentContext(
                "user-1", "session-1", "查花呗，查余额，查交易",
                List.of(), session, "测试提示词", null));

        assertThat(result.executedTools()).containsExactly(
                "get_credit_summary", "get_balance", "list_transactions");
        assertThat(result.finalContent())
                .contains("总额度 5,000.00 元，已用 100.00 元")
                .contains("可用余额为 888.00 元")
                .contains("找到 1 条交易明细")
                .doesNotContain("暂时", "推测");
        verify(languageModel, never()).agentStep(any(), any());
    }
}
