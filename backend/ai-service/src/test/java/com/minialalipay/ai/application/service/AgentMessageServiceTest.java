package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.domain.agent.*;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    private AgentMessageService service;

    @BeforeEach
    void setUp() {
        lenient().when(injectionDetector.check(any()))
                .thenReturn(InjectionDetector.InjectionCheckResult.SAFE);
        service = new AgentMessageService(
                sessionRepository, messageRepository, languageModelPort,
                injectionDetector, "30m");
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

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-001", null, "查询余额", NOW);

        assertThat(result.sessionId()).isNotNull();
        assertThat(result.content()).isEqualTo("测试回复");
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

        AgentMessageService.SendMessageResult result = service.processMessage(
                USER_ID, "client-msg-001", "01J5Q000000000000000000001",
                "转账 100 元", NOW);

        assertThat(result.slots()).containsEntry("amountFen", 10000L);
    }
}
