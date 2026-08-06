package com.minialalipay.ai.application.service;

import com.minialalipay.ai.domain.agent.AgentSession;
import com.minialalipay.ai.domain.tool.ConfirmationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPolicyServiceTest {

    private static final String USER_ID = "01J5Q000000000000000000002";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));

    private ToolPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new ToolPolicyService(
                new com.minialalipay.ai.domain.tool.ToolCatalog(), FIXED_CLOCK);
    }

    @Test
    void shouldAllowReadOnlyTool() {
        AgentSession session = new AgentSession("s1", USER_ID, NOW);
        ToolPolicyService.PolicyDecision decision = policyService.evaluate("get_balance", session);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void shouldAllowDraftTool() {
        AgentSession session = new AgentSession("s1", USER_ID, NOW);
        ToolPolicyService.PolicyDecision decision = policyService.evaluate("create_transfer_draft", session);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void shouldRejectHighRiskWriteWithoutConfirmation() {
        AgentSession session = new AgentSession("s1", USER_ID, NOW);
        ToolPolicyService.PolicyDecision decision = policyService.evaluate("submit_confirmed_transfer", session);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.needsConfirmationContext()).isTrue();
    }

    @Test
    void shouldRejectClosedSession() {
        AgentSession session = new AgentSession("s1", USER_ID, NOW);
        session.close();
        ToolPolicyService.PolicyDecision decision = policyService.evaluate("get_balance", session);
        assertThat(decision.allowed()).isFalse();
    }

    // ---- 阶段五：确认上下文测试 ----

    @Test
    void shouldAllowHighRiskWriteWithValidConfirmation() {
        AgentSession session = new AgentSession("s1", USER_ID, NOW);
        Map<String, Object> params = Map.of("draftId", "draft-001");

        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("draftId", "draft-001"), 5);
        policyService.registerConfirmation(ctx);

        ToolPolicyService.PolicyDecision decision = policyService.evaluateWithConfirmation(
                "submit_confirmed_transfer", session, params, ctx.getConfirmationHandle());

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void shouldRejectHighRiskWriteWithNullHandle() {
        AgentSession session = new AgentSession("s1", USER_ID, NOW);
        Map<String, Object> params = Map.of("draftId", "draft-001");

        ToolPolicyService.PolicyDecision decision = policyService.evaluateWithConfirmation(
                "submit_confirmed_transfer", session, params, null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.needsConfirmationContext()).isTrue();
    }

    @Test
    void shouldRejectConfirmationHandleReuse() {
        AgentSession session = new AgentSession("s1", USER_ID, NOW);
        Map<String, Object> params = Map.of("draftId", "draft-001");

        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("draftId", "draft-001"), 5);
        policyService.registerConfirmation(ctx);

        // 第一次使用成功
        ToolPolicyService.PolicyDecision first = policyService.evaluateWithConfirmation(
                "submit_confirmed_transfer", session, params, ctx.getConfirmationHandle());
        assertThat(first.allowed()).isTrue();

        // 第二次使用——句柄已被消费，应拒绝
        ToolPolicyService.PolicyDecision second = policyService.evaluateWithConfirmation(
                "submit_confirmed_transfer", session, params, ctx.getConfirmationHandle());
        assertThat(second.allowed()).isFalse();
        assertThat(second.reason()).contains("无效");
    }

    @Test
    void shouldRejectProtectedParamOverride() {
        AgentSession session = new AgentSession("s1", USER_ID, NOW);
        // LLM 尝试在调用高风险工具时覆盖付款账户
        Map<String, Object> params = Map.of("draftId", "draft-001",
                "payerAccountId", "hacked-account");

        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("draftId", "draft-001"), 5);
        policyService.registerConfirmation(ctx);

        // 受保护字段的篡改会被拒绝
        try {
            policyService.evaluateWithConfirmation(
                    "submit_confirmed_transfer", session, params, ctx.getConfirmationHandle());
        } catch (Exception e) {
            assertThat(e.getMessage()).contains("不安全");
        }
    }
}
