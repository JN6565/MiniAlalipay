package com.minialalipay.ai.application.service;

import com.minialalipay.ai.domain.agent.AgentSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPolicyServiceTest {

    private static final String USER_ID = "01J5Q000000000000000000002";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    private ToolPolicyService policyService;

    @BeforeEach
    void setUp() {
        policyService = new ToolPolicyService(
                new com.minialalipay.ai.domain.tool.ToolCatalog());
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
    void shouldAlwaysRejectHighRiskWriteInStage4() {
        // 阶段四：高风险资金工具一律 fail-closed，即使会话设置了确认令牌槽位。
        // 真实可信确认注入、消费和提交由阶段五实现。
        AgentSession session = new AgentSession("s1", USER_ID, NOW);
        session.setSlot("confirmationToken", "test-token-001");
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
}
