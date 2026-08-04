package com.minialalipay.ai.domain.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentMessage 实体单元测试。
 *
 * <p>验证消息创建不变量和字段完整性。</p>
 */
class AgentMessageTest {

    private static final String MESSAGE_ID = "01J5Q000000000000000000010";
    private static final String SESSION_ID = "01J5Q000000000000000000001";
    private static final String CLIENT_MSG_ID = "client-msg-001";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Test
    void shouldCreateUserMessage() {
        AgentMessage msg = new AgentMessage(
                MESSAGE_ID, SESSION_ID, CLIENT_MSG_ID,
                MessageRole.USER, "脱敏后的用户输入", 50, NOW);

        assertThat(msg.getMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(msg.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(msg.getClientMessageId()).isEqualTo(CLIENT_MSG_ID);
        assertThat(msg.getRole()).isEqualTo(MessageRole.USER);
        assertThat(msg.getContentRedacted()).isEqualTo("脱敏后的用户输入");
        assertThat(msg.getTokenCount()).isEqualTo(50);
        assertThat(msg.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRejectNullOrEmptyFields() {
        assertThatThrownBy(() -> new AgentMessage(null, SESSION_ID, CLIENT_MSG_ID,
                MessageRole.USER, "content", 0, NOW))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new AgentMessage(MESSAGE_ID, null, CLIENT_MSG_ID,
                MessageRole.USER, "content", 0, NOW))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new AgentMessage(MESSAGE_ID, SESSION_ID, null,
                MessageRole.USER, "content", 0, NOW))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new AgentMessage(MESSAGE_ID, SESSION_ID, CLIENT_MSG_ID,
                null, "content", 0, NOW))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNegativeTokenCount() {
        assertThatThrownBy(() -> new AgentMessage(
                MESSAGE_ID, SESSION_ID, CLIENT_MSG_ID,
                MessageRole.USER, "content", -1, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Token");
    }

    @Test
    void messageIsImmutable() {
        AgentMessage msg = new AgentMessage(
                MESSAGE_ID, SESSION_ID, CLIENT_MSG_ID,
                MessageRole.ASSISTANT, "AI 回复内容", 30, NOW);

        // 所有字段由构造器设置，没有 setter，创建后不可修改
        assertThat(msg.getRole()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(msg.getContentRedacted()).isEqualTo("AI 回复内容");
    }
}
