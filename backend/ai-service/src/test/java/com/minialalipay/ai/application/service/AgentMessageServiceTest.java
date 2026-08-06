package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.application.port.ToolResult;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.ToolAuditService;
import com.minialalipay.ai.application.service.ResultInterpreter;
import com.minialalipay.ai.application.service.ToolPolicyService;
import com.minialalipay.ai.domain.agent.*;
import com.minialalipay.ai.domain.tool.ToolRiskLevel;
import com.minialalipay.ai.infrastructure.client.ToolRouter;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentMessageServiceTest {

    private static final String USER_ID = "01J5Q000000000000000000002";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Mock AgentSessionRepository sessionRepository;
    @Mock AgentMessageRepository messageRepository;
    @Mock LanguageModelPort languageModelPort;
    @Mock InjectionDetector injectionDetector;
    @Mock ToolRouter toolRouter;
    @Mock ToolPolicyService toolPolicy;
    @Mock ToolAuditService toolAudit;
    @Mock ResultInterpreter resultInterpreter;

    private AgentMessageService service;

    @BeforeEach
    void setUp() {
        lenient().when(injectionDetector.check(any()))
                .thenReturn(InjectionDetector.InjectionCheckResult.SAFE);
        service = new AgentMessageService(
                sessionRepository, messageRepository, languageModelPort,
                injectionDetector, toolRouter, toolPolicy, toolAudit,
                resultInterpreter, "30m", true);
    }

    @Test
    void shouldCreateNewSessionWhenSessionIdIsNull() {
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt()))
                .thenReturn(List.of());
        when(languageModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResponse("测试回复", IntentType.BALANCE_QUERY,
                        Map.of(), 10, false));
        when(toolPolicy.evaluate(eq("get_balance"), any()))
                .thenReturn(new ToolPolicyService.PolicyDecision(
                        true, ToolRiskLevel.READ_ONLY, null, false));
        when(toolRouter.route(eq("get_balance"), any(), any()))
                .thenReturn(new ToolResult("SUCCESS", Map.of("availableFen", 1_000_000L), null, 0));
        when(resultInterpreter.interpret(eq("get_balance"), any()))
                .thenReturn("您当前账户可用余额为 10,000.00 元。");

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-001", null, "查询余额", NOW);

        assertThat(result.sessionId()).isNotNull();
        assertThat(result.content()).isEqualTo("您当前账户可用余额为 10,000.00 元。");
        verify(sessionRepository, atLeastOnce()).save(any(AgentSession.class));
    }

    @Test
    void shouldRejectInactiveSession() {
        AgentSession closedSession = new AgentSession(
                "01J5Q000000000000000000001", USER_ID,"", Map.of(),
                AgentSessionStatus.CLOSED, 0L, NOW, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(closedSession));

        assertThatThrownBy(() -> service.processMessage(
                USER_ID, "client-msg-001", "01J5Q000000000000000000001",
                "测试", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code")
                .isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    void shouldRejectWrongUserId() {
        AgentSession otherSession = new AgentSession(
                "01J5Q000000000000000000001", "DIFFERENT_USER", "", Map.of(),
                AgentSessionStatus.ACTIVE, 0L, NOW, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(otherSession));

        assertThatThrownBy(() -> service.processMessage(
                USER_ID, "client-msg-001", "01J5Q000000000000000000001",
                "测试", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code")
                .isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    void shouldReturnCachedResponseForDuplicateClientMessageId() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        // 已存在用户消息和助手消息
        AgentMessage userMsg = new AgentMessage("msg-1", "01J5Q000000000000000000001",
                "client-msg-dup", MessageRole.USER, "查询余额", 4, NOW);
        AgentMessage assistantMsg = new AgentMessage("msg-2", "01J5Q000000000000000000001",
                "client-msg-dup", MessageRole.ASSISTANT, "您的余额是...", 10, NOW);
        when(messageRepository.findByClientMessageId(
                "01J5Q000000000000000000001", "client-msg-dup", MessageRole.USER))
                .thenReturn(Optional.of(userMsg));
        when(messageRepository.findByClientMessageId(
                "01J5Q000000000000000000001", "client-msg-dup", MessageRole.ASSISTANT))
                .thenReturn(Optional.of(assistantMsg));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-dup", "01J5Q000000000000000000001",
                "查询余额", NOW);

        assertThat(result.fromCache()).isTrue();
        assertThat(result.content()).isEqualTo("您的余额是...");
        verify(languageModelPort, never()).chat(any(), any(), any());
    }

    @Test
    void shouldUpdateSlotsOnNewInfo() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(languageModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResponse("好的", IntentType.TRANSFER,
                        Map.of("amountFen", 10000L), 20, false));

        when(toolPolicy.evaluate(any(), any()))
                .thenReturn(new ToolPolicyService.PolicyDecision(
                        true, ToolRiskLevel.DRAFT, null, false));
        when(toolRouter.route(any(), any(), any()))
                .thenReturn(new ToolResult("SUCCESS",
                        Map.of("draftId", "01J5Q000000000000000000120"), null, 0));
        when(resultInterpreter.interpret(any(), any()))
                .thenReturn("好的，请核对信息");

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-001", "01J5Q000000000000000000001",
                "转账 100 元", NOW);

        assertThat(result.slots()).containsEntry("amountFen", 10000L);
    }

    @Test
    @DisplayName("BALANCE_QUERY 意图执行 get_balance 工具并返回解释结果")
    void shouldExecuteGetBalanceForBalanceQuery() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(languageModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResponse("查询中...", IntentType.BALANCE_QUERY,
                        Map.of(), 10, false));

        when(toolPolicy.evaluate(eq("get_balance"), any()))
                .thenReturn(new ToolPolicyService.PolicyDecision(
                        true, ToolRiskLevel.READ_ONLY, null, false));

        ToolResult balanceResult = new ToolResult("SUCCESS",
                Map.of("availableFen", 1_000_000L, "frozenFen", 0L), null, 50);
        when(toolRouter.route(eq("get_balance"), any(), eq(USER_ID)))
                .thenReturn(balanceResult);

        when(resultInterpreter.interpret(eq("get_balance"), any()))
                .thenReturn("您当前账户可用余额为 10,000.00 元。");

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-010", "01J5Q000000000000000000001",
                "查余额", NOW);

        assertThat(result.intent()).isEqualTo(IntentType.BALANCE_QUERY);
        assertThat(result.content()).isEqualTo("您当前账户可用余额为 10,000.00 元。");
        assertThat(result.clarificationNeeded()).isFalse();
        verify(toolRouter).route(eq("get_balance"), any(), eq(USER_ID));
        verify(resultInterpreter).interpret(eq("get_balance"), any());
    }

    @Test
    @DisplayName("TRANSFER 意图链式执行 create→validate→prepare 三个工具")
    void shouldChainExecuteTransferTools() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(languageModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResponse("好的", IntentType.TRANSFER,
                        Map.of("payeeId", "01J5Q000000000000000000010",
                               "amountFen", 10000L), 15, false));

        when(toolPolicy.evaluate(any(), any()))
                .thenReturn(new ToolPolicyService.PolicyDecision(
                        true, ToolRiskLevel.DRAFT, null, false));

        ToolResult draftResult = new ToolResult("SUCCESS",
                Map.of("draftId", "01J5Q000000000000000000120", "version", 0L), null, 30);
        when(toolRouter.route(eq("create_transfer_draft"), any(), eq(USER_ID)))
                .thenReturn(draftResult);

        ToolResult validateResult = new ToolResult("SUCCESS",
                Map.of("valid", true, "checks", Map.of("balanceCheck", "PASS")), null, 25);
        when(toolRouter.route(eq("validate_transfer_draft"), any(), eq(USER_ID)))
                .thenReturn(validateResult);

        ToolResult cardResult = new ToolResult("SUCCESS",
                Map.of("cardType", "TRANSFER_CONFIRMATION",
                       "payeeNickname", "张三", "amountFen", 10000L), null, 20);
        when(toolRouter.route(eq("prepare_confirmation_card"), any(), eq(USER_ID)))
                .thenReturn(cardResult);

        // processMessage 使用最后一个工具结果（prepare_confirmation_card）调用 ResultInterpreter
        when(resultInterpreter.interpret(eq("prepare_confirmation_card"), any()))
                .thenReturn("请核对以下信息后完成支付：\n收款人: 张三\n金额: 100.00 元");

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-011", "01J5Q000000000000000000001",
                "转给张三100元", NOW);

        assertThat(result.content()).contains("张三");
        verify(toolRouter).route(eq("create_transfer_draft"), any(), eq(USER_ID));
        verify(toolRouter).route(eq("validate_transfer_draft"), any(), eq(USER_ID));
        verify(toolRouter).route(eq("prepare_confirmation_card"), any(), eq(USER_ID));
    }

    @Test
    @DisplayName("TRANSFER 中间工具失败时中止链式调用")
    void shouldStopChainOnIntermediateFailure() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(languageModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResponse("好的", IntentType.TRANSFER,
                        Map.of("payeeId", "01J5Q000000000000000000010",
                               "amountFen", 10000L), 15, false));

        when(toolPolicy.evaluate(any(), any()))
                .thenReturn(new ToolPolicyService.PolicyDecision(
                        true, ToolRiskLevel.DRAFT, null, false));

        ToolResult draftResult = new ToolResult("SUCCESS",
                Map.of("draftId", "01J5Q000000000000000000120"), null, 30);
        when(toolRouter.route(eq("create_transfer_draft"), any(), eq(USER_ID)))
                .thenReturn(draftResult);

        ToolResult failResult = new ToolResult("INSUFFICIENT_BALANCE", Map.of(),
                "余额不足，当前可用余额为 50.00 元", 20);
        when(toolRouter.route(eq("validate_transfer_draft"), any(), eq(USER_ID)))
                .thenReturn(failResult);

        when(resultInterpreter.interpret(eq("validate_transfer_draft"), any()))
                .thenReturn("余额不足，无法完成支付。请查看余额后调低转账金额。");

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-012", "01J5Q000000000000000000001",
                "转给张三100元", NOW);

        assertThat(result.content()).contains("余额不足");
        verify(toolRouter, never()).route(eq("prepare_confirmation_card"), any(), any());
    }

    @Test
    @DisplayName("意图需要澄清时不执行工具，直接返回 LLM 澄清文本")
    void shouldSkipToolsWhenClarificationNeeded() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(languageModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResponse("好的，请告诉我收款人是谁，以及转账金额是多少？",
                        IntentType.TRANSFER, Map.of(), 30, true));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-013", "01J5Q000000000000000000001",
                "转账给张三", NOW);

        assertThat(result.clarificationNeeded()).isTrue();
        verify(toolRouter, never()).route(any(), any(), any());
    }

    @Test
    @DisplayName("UNKNOWN 意图不执行工具，直接返回 LLM 兜底回复")
    void shouldSkipToolsForUnknownIntent() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(languageModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResponse("抱歉，我没有理解您的意图...",
                        IntentType.UNKNOWN, Map.of(), 40, true));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-014", "01J5Q000000000000000000001",
                "今天天气怎么样", NOW);

        assertThat(result.intent()).isEqualTo(IntentType.UNKNOWN);
        verify(toolRouter, never()).route(any(), any(), any());
    }

    @Test
    @DisplayName("工具调用失败时返回降级话术，不影响会话状态")
    void shouldReturnFallbackOnToolFailure() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(languageModelPort.chat(any(), any(), any()))
                .thenReturn(new ChatResponse("查询中...", IntentType.BALANCE_QUERY,
                        Map.of(), 10, false));

        when(toolPolicy.evaluate(eq("get_balance"), any()))
                .thenReturn(new ToolPolicyService.PolicyDecision(
                        true, ToolRiskLevel.READ_ONLY, null, false));

        ToolResult failResult = new ToolResult("TOOL_UNAVAILABLE", Map.of(),
                "工具调用超时，请稍后重试", 3000);
        when(toolRouter.route(eq("get_balance"), any(), eq(USER_ID)))
                .thenReturn(failResult);

        when(resultInterpreter.interpret(eq("get_balance"), any()))
                .thenReturn("暂时无法查询余额，请稍后重试或刷新页面。");

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-015", "01J5Q000000000000000000001",
                "查余额", NOW);

        assertThat(result.content()).isNotNull();
        verify(sessionRepository, atLeastOnce()).save(any(AgentSession.class));
    }
}
