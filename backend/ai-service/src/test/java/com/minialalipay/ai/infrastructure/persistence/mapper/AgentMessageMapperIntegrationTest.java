package com.minialalipay.ai.infrastructure.persistence.mapper;

import com.minialalipay.ai.infrastructure.persistence.po.AgentMessagePO;
import com.minialalipay.ai.infrastructure.persistence.po.AgentSessionPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentMessageMapper 集成测试。
 *
 * <p>验证消息插入、幂等查询、会话消息查询和唯一约束。</p>
 */
@MybatisTest
@ActiveProfiles("test")
@Sql(scripts = "classpath:db/test-schema.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AgentMessageMapperIntegrationTest {

    @Autowired
    private AgentMessageMapper messageMapper;

    @Autowired
    private AgentSessionMapper sessionMapper;

    private static final String SESSION_ID = "01J5Q000000000000000000001";
    private static final String MESSAGE_ID = "01J5Q000000000000000000010";
    private static final String USER_ID = "01J5Q000000000000000000002";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @BeforeEach
    void setUp() {
        AgentSessionPO session = new AgentSessionPO(
                SESSION_ID, USER_ID, null, null, "ACTIVE", 0L, NOW, NOW);
        sessionMapper.insert(session);
    }

    @Test
    void shouldInsertAndFindMessage() {
        AgentMessagePO msg = new AgentMessagePO(
                MESSAGE_ID, SESSION_ID, "client-msg-001",
                "USER", "脱敏用户输入", 50, NOW);
        messageMapper.insert(msg);

        AgentMessagePO found = messageMapper.findByMessageId(MESSAGE_ID);
        assertThat(found).isNotNull();
        assertThat(found.getRole()).isEqualTo("USER");
        assertThat(found.getContentRedacted()).isEqualTo("脱敏用户输入");
    }

    @Test
    void shouldRejectDuplicateClientMessageIdAndRole() {
        AgentMessagePO first = new AgentMessagePO(
                MESSAGE_ID, SESSION_ID, "client-msg-dup",
                "USER", "第一条消息", 10, NOW);
        messageMapper.insert(first);

        AgentMessagePO dup = new AgentMessagePO(
                "01J5Q000000000000000000011", SESSION_ID, "client-msg-dup",
                "USER", "重复消息", 10, NOW);

        assertThatThrownBy(() -> messageMapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void shouldFindMessagesByClientMessageId() {
        AgentMessagePO msg = new AgentMessagePO(
                MESSAGE_ID, SESSION_ID, "client-msg-002",
                "USER", "用户输入", 30, NOW);
        messageMapper.insert(msg);

        List<AgentMessagePO> found = messageMapper.findByClientMessageId(SESSION_ID, "client-msg-002");
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRole()).isEqualTo("USER");
    }

    @Test
    void shouldFindMessagesBySessionIdOrderedByTime() {
        messageMapper.insert(new AgentMessagePO(
                "01J5Q000000000000000000020", SESSION_ID, "c1", "USER", "msg1", 10, NOW));
        messageMapper.insert(new AgentMessagePO(
                "01J5Q000000000000000000021", SESSION_ID, "c2", "ASSISTANT", "msg2", 20, NOW.plusSeconds(1)));

        List<AgentMessagePO> messages = messageMapper.findBySessionId(SESSION_ID, 100);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getMessageId()).isEqualTo("01J5Q000000000000000000020");
        assertThat(messages.get(1).getMessageId()).isEqualTo("01J5Q000000000000000000021");
    }
}
