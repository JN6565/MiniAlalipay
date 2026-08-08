package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.domain.agent.*;
import com.minialalipay.common.error.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

/**
 * AI 消息处理应用服务测试。
 *
 * <p>AgentLoop 重构后，工具编排、策略评估与结果解释已整体下沉到 {@link AgentLoop}，
 * 本测试聚焦服务层的会话生命周期、幂等、上下文构建与 AgentResult 到发送结果的映射。</p>
 */
@ExtendWith(MockitoExtension.class)
class AgentMessageServiceTest {

    private static final String USER_ID = "01J5Q000000000000000000002";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Mock AgentSessionRepository sessionRepository;
    @Mock AgentMessageRepository messageRepository;
    @Mock LanguageModelPort languageModelPort;
    @Mock InjectionDetector injectionDetector;
    @Mock AgentLoop agentLoop;
    @Mock UserPreferenceService userPreferenceService;

    private AgentMessageService service;

    @BeforeEach
    void setUp() {
        lenient().when(injectionDetector.check(any()))
                .thenReturn(InjectionDetector.InjectionCheckResult.SAFE);
        service = new AgentMessageService(
                sessionRepository, messageRepository, languageModelPort,
                injectionDetector, agentLoop, userPreferenceService,
                new ObjectMapper(), "30m", "你是吱托芙，AI支付助手。");
    }

    /** 构造 AgentLoop 执行结果（同步模式，无工具结果消息与过渡文本）。 */
    private static AgentLoop.AgentResult agentResult(String content, List<String> tools,
                                                     Map<String, Object> slots) {
        return new AgentLoop.AgentResult(content, tools, slots, 0, 1);
    }

    @Test
    void shouldCreateNewSessionWhenSessionIdIsNull() {
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt()))
                .thenReturn(List.of());
        when(agentLoop.execute(any())).thenReturn(agentResult(
                "您当前账户可用余额为 10,000.00 元。",
                List.of("get_balance"),
                new HashMap<>(Map.of("availableFen", 1_000_000L))));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-001", null, "查询余额", null, NOW);

        assertThat(result.sessionId()).isNotNull();
        assertThat(result.content()).isEqualTo("您当前账户可用余额为 10,000.00 元。");
        verify(sessionRepository, atLeastOnce()).save(any(AgentSession.class));
    }

    @Test
    void shouldRejectInactiveSession() {
        AgentSession closedSession = new AgentSession(
                "01J5Q000000000000000000001", USER_ID,"", null, Map.of(),
                AgentSessionStatus.CLOSED, 0L, NOW, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(closedSession));

        assertThatThrownBy(() -> service.processMessage(
                USER_ID, "client-msg-001", "01J5Q000000000000000000001",
                "测试", null, NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code")
                .isEqualTo("SESSION_NOT_FOUND");
    }

    @Test
    void shouldRejectWrongUserId() {
        AgentSession otherSession = new AgentSession(
                "01J5Q000000000000000000001", "DIFFERENT_USER", "", null, Map.of(),
                AgentSessionStatus.ACTIVE, 0L, NOW, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(otherSession));

        assertThatThrownBy(() -> service.processMessage(
                USER_ID, "client-msg-001", "01J5Q000000000000000000001",
                "测试", null, NOW))
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
                "查询余额", null, NOW);

        assertThat(result.fromCache()).isTrue();
        assertThat(result.content()).isEqualTo("您的余额是...");
        verify(agentLoop, never()).execute(any());
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
        when(agentLoop.execute(any())).thenReturn(agentResult(
                "好的，请核对信息",
                List.of("create_transfer_draft"),
                new HashMap<>(Map.of("amountFen", 10000L))));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-001", "01J5Q000000000000000000001",
                "转账 100 元", null, NOW);

        assertThat(result.slots()).containsEntry("amountFen", 10000L);
    }

    @Test
    @DisplayName("BALANCE_QUERY 意图经 AgentLoop 执行 get_balance 并返回解释结果")
    void shouldExecuteGetBalanceForBalanceQuery() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(agentLoop.execute(any())).thenReturn(agentResult(
                "您当前账户可用余额为 10,000.00 元。",
                List.of("get_balance"),
                new HashMap<>(Map.of("availableFen", 1_000_000L, "frozenFen", 0L))));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-010", "01J5Q000000000000000000001",
                "查余额", null, NOW);

        assertThat(result.intent()).isEqualTo(IntentType.BALANCE_QUERY);
        assertThat(result.content()).isEqualTo("您当前账户可用余额为 10,000.00 元。");
        assertThat(result.clarificationNeeded()).isFalse();
    }

    @Test
    @DisplayName("TRANSFER 意图经 AgentLoop 链式执行 create→validate→prepare 三个工具")
    void shouldChainExecuteTransferTools() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(agentLoop.execute(any())).thenReturn(agentResult(
                "请核对以下信息后完成支付：\n收款人: 张三\n金额: 100.00 元",
                List.of("search_payees", "create_transfer_draft",
                        "validate_transfer_draft", "prepare_confirmation_card"),
                new HashMap<>(Map.of("payeeId", "01J5Q000000000000000000010", "amountFen", 10000L))));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-011", "01J5Q000000000000000000001",
                "转给张三100元", null, NOW);

        assertThat(result.content()).contains("张三");
        assertThat(result.intent()).isEqualTo(IntentType.TRANSFER);
    }

    @Test
    @DisplayName("TRANSFER 中间工具失败时 AgentLoop 返回失败话术")
    void shouldStopChainOnIntermediateFailure() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(agentLoop.execute(any())).thenReturn(agentResult(
                "余额不足，无法完成支付。请查看余额后调低转账金额。",
                List.of("search_payees", "create_transfer_draft", "validate_transfer_draft"),
                new HashMap<>()));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-012", "01J5Q000000000000000000001",
                "转给张三100元", null, NOW);

        assertThat(result.content()).contains("余额不足");
    }

    @Test
    @DisplayName("澄清类回复由 AgentLoop 直达，服务层原样返回且无工具执行副作用")
    void shouldSkipToolsWhenClarificationNeeded() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(agentLoop.execute(any())).thenReturn(agentResult(
                "好的，请告诉我收款人是谁，以及转账金额是多少？",
                List.of(), new HashMap<>()));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-013", "01J5Q000000000000000000001",
                "转账给张三", null, NOW);

        assertThat(result.content()).contains("请告诉我");
        assertThat(result.intent()).isEqualTo(IntentType.UNKNOWN);
        assertThat(result.clarificationNeeded()).isFalse();
    }

    @Test
    @DisplayName("UNKNOWN 意图无工具执行，服务层返回 AgentLoop 兜底回复")
    void shouldSkipToolsForUnknownIntent() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(agentLoop.execute(any())).thenReturn(agentResult(
                "抱歉，我没有理解您的意图...",
                List.of(), new HashMap<>()));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-014", "01J5Q000000000000000000001",
                "今天天气怎么样", null, NOW);

        assertThat(result.intent()).isEqualTo(IntentType.UNKNOWN);
    }

    @Test
    @DisplayName("工具调用失败时 AgentLoop 返回降级话术，不影响会话状态")
    void shouldReturnFallbackOnToolFailure() {
        AgentSession session = new AgentSession(
                "01J5Q000000000000000000001", USER_ID, NOW);
        when(sessionRepository.findById("01J5Q000000000000000000001"))
                .thenReturn(Optional.of(session));
        when(messageRepository.findByClientMessageId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(messageRepository.findRecentBySessionId(any(), anyInt())).thenReturn(List.of());
        when(agentLoop.execute(any())).thenReturn(agentResult(
                "暂时无法查询余额，请稍后重试或刷新页面。",
                List.of("get_balance"), new HashMap<>()));

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-015", "01J5Q000000000000000000001",
                "查余额", null, NOW);

        assertThat(result.content()).isNotNull();
        verify(sessionRepository, atLeastOnce()).save(any(AgentSession.class));
    }
}
