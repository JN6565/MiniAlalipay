package com.minialalipay.ai.domain.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 领域枚举解析测试。
 *
 * <p>覆盖 AgentSessionStatus、MessageRole、IntentType 三个枚举的
 * 合法值解析和未知值异常处理。</p>
 */
class DomainEnumTest {

    // ---- AgentSessionStatus ----

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "CLOSED", "EXPIRED"})
    void shouldParseValidSessionStatus(String name) {
        AgentSessionStatus status = AgentSessionStatus.valueOf(name);
        assertThat(status.name()).isEqualTo(name);
    }

    @Test
    void shouldRejectUnknownSessionStatus() {
        assertThatThrownBy(() -> AgentSessionStatus.valueOf("DELETED"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionStatusShouldHaveThreeValues() {
        assertThat(AgentSessionStatus.values()).hasSize(3);
    }

    // ---- MessageRole ----

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ASSISTANT", "SYSTEM"})
    void shouldParseValidMessageRole(String name) {
        MessageRole role = MessageRole.valueOf(name);
        assertThat(role.name()).isEqualTo(name);
    }

    @Test
    void shouldRejectUnknownMessageRole() {
        assertThatThrownBy(() -> MessageRole.valueOf("BOT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void messageRoleShouldHaveThreeValues() {
        assertThat(MessageRole.values()).hasSize(3);
    }

    // ---- IntentType ----

    @ParameterizedTest
    @EnumSource(IntentType.class)
    void everyIntentShouldBeParseable(IntentType intent) {
        IntentType parsed = IntentType.valueOf(intent.name());
        assertThat(parsed).isEqualTo(intent);
    }

    @Test
    void intentTypeShouldHaveEightValues() {
        // TRANSFER, BALANCE_QUERY, TRANSACTION_LIST, TRANSACTION_STATUS,
        // USER_SEARCH, CREDIT_SUMMARY, CREDIT_BILL, CREDIT_REPAYMENT
        assertThat(IntentType.values()).hasSize(8);
    }

    @Test
    void shouldRejectUnknownIntent() {
        assertThatThrownBy(() -> IntentType.valueOf("PAY_BILL"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
