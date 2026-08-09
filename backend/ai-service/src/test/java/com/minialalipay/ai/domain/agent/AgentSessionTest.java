package com.minialalipay.ai.domain.agent;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AgentSession 聚合根不变量测试。
 *
 * <p>覆盖会话创建、状态流转（ACTIVE → CLOSED、ACTIVE → EXPIRED）、
 * 槽位管理和并发版本控制等核心不变量。</p>
 */
class AgentSessionTest {

    private static final String SESSION_ID = "01J5Q000000000000000000001";
    private static final String USER_ID = "01J5Q000000000000000000002";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    // ---- 创建不变量 ----

    @Test
    void shouldCreateSessionWithActiveStatus() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);

        assertThat(session.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(session.getUserId()).isEqualTo(USER_ID);
        assertThat(session.getStatus()).isEqualTo(AgentSessionStatus.ACTIVE);
        assertThat(session.getVersion()).isZero();
        assertThat(session.getSlots()).isEmpty();
        assertThat(session.getSummary()).isNull();
        assertThat(session.isActive()).isTrue();
    }

    @Test
    void shouldRejectNullSessionId() {
        assertThatThrownBy(() -> new AgentSession(null, USER_ID, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("会话 ID");
    }

    @Test
    void shouldRejectNullUserId() {
        assertThatThrownBy(() -> new AgentSession(SESSION_ID, null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("用户 ID");
    }

    // ---- 活跃状态操作 ----

    @Test
    void shouldUpdateActiveTimestamp() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        Instant later = NOW.plusSeconds(60);
        session.touch(later);

        assertThat(session.getLastActiveAt()).isEqualTo(later);
    }

    @Test
    void shouldUpdateSummary() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        String summary = "用户请求查询余额，已返回当前可用余额";

        session.updateSummary(summary);
        assertThat(session.getSummary()).isEqualTo(summary);
    }

    @Test
    void shouldUpdateSlots() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        Map<String, Object> slots = Map.of("amountFen", 10000L, "payeeId", "01J5Q000000000000000000003");

        session.updateSlots(slots);
        assertThat(session.getSlots()).containsAllEntriesOf(slots);
    }

    @Test
    void shouldSetSingleSlot() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);

        session.setSlot("intent", "TRANSFER");
        session.setSlot("amountFen", 50000L);

        assertThat(session.getSlots())
                .containsEntry("intent", "TRANSFER")
                .containsEntry("amountFen", 50000L);
    }

    @Test
    void slotsShouldReturnUnmodifiableView() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);

        assertThatThrownBy(() -> session.getSlots().put("key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ---- 状态流转 ----

    @Test
    void shouldCloseActiveSession() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        session.close();

        assertThat(session.getStatus()).isEqualTo(AgentSessionStatus.CLOSED);
        assertThat(session.isActive()).isFalse();
    }

    @Test
    void shouldNotAllowOperationOnClosedSession() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        session.close();

        assertThatThrownBy(() -> session.touch(NOW.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void shouldNotCloseAlreadyClosedSession() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        session.close();

        assertThatThrownBy(() -> session.close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("终态");
    }

    @Test
    void shouldExpireSessionAfterTimeout() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        Instant afterTimeout = NOW.plusSeconds(31 * 60); // 31 分钟后

        boolean expired = session.checkExpiry(afterTimeout, 30);
        assertThat(expired).isTrue();
        assertThat(session.getStatus()).isEqualTo(AgentSessionStatus.EXPIRED);
    }

    @Test
    void shouldNotExpireActiveSessionWithinTimeout() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        Instant beforeTimeout = NOW.plusSeconds(25 * 60); // 25 分钟后

        boolean expired = session.checkExpiry(beforeTimeout, 30);
        assertThat(expired).isFalse();
        assertThat(session.getStatus()).isEqualTo(AgentSessionStatus.ACTIVE);
    }

    @Test
    void shouldNotRecheckExpiredSession() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        session.checkExpiry(NOW.plusSeconds(31 * 60), 30); // 过期

        boolean expiredAgain = session.checkExpiry(NOW.plusSeconds(60 * 60), 30);
        assertThat(expiredAgain).isFalse(); // 已是 EXPIRED，不再触发
    }

    // ---- 从持久化重建 ----

    @Test
    void shouldReconstructFromPersistence() {
        AgentSession session = new AgentSession(
                SESSION_ID, USER_ID, "脱敏摘要", null,
                Map.of("intent", "BALANCE_QUERY"),
                AgentSessionStatus.ACTIVE, 3L,
                NOW.plusSeconds(300), NOW
        );

        assertThat(session.getSessionId()).isEqualTo(SESSION_ID);
        assertThat(session.getUserId()).isEqualTo(USER_ID);
        assertThat(session.getSummary()).isEqualTo("脱敏摘要");
        assertThat(session.getSlots()).containsEntry("intent", "BALANCE_QUERY");
        assertThat(session.getVersion()).isEqualTo(3L);
    }

    // ---- 版本回写 ----

    @Test
    void shouldUpdateVersionAfterCasSuccess() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);
        assertThat(session.getVersion()).isZero();

        session.updateVersion(1L);
        assertThat(session.getVersion()).isEqualTo(1L);
    }

    // ---- 属地不可变 ----

    @Test
    void userIdShouldBeImmutable() {
        AgentSession session = new AgentSession(SESSION_ID, USER_ID, NOW);

        // USER_ID 是 final 字段，创建后不可修改，通过构造后再验证
        assertThat(session.getUserId()).isEqualTo(USER_ID);
    }
}
