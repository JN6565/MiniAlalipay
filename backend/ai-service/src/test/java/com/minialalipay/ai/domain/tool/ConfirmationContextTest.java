package com.minialalipay.ai.domain.tool;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 确认上下文领域测试：验证句柄一次性消费、过期、约束锁定等安全不变量。
 */
class ConfirmationContextTest {

    private static final String USER_ID = "01J5Q000000000000000000001";

    @Test
    void shouldConsumeSuccessfully() {
        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("amountFen", 50000L), 5);

        ctx.consume(USER_ID, "submit_confirmed_transfer", Instant.now().plusSeconds(60));
        assertThat(ctx.isConsumed()).isTrue();
    }

    @Test
    void shouldRejectDoubleConsumption() {
        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("amountFen", 50000L), 5);

        Instant now = Instant.now().plusSeconds(10);
        ctx.consume(USER_ID, "submit_confirmed_transfer", now);

        assertThatThrownBy(() ->
                ctx.consume(USER_ID, "submit_confirmed_transfer", now.plusSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已被消费");
    }

    @Test
    void shouldRejectExpiredContext() {
        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("amountFen", 50000L), 5);

        Instant now = Instant.now().plusSeconds(400); // 超过5分钟
        assertThat(ctx.isExpired(now)).isTrue();

        assertThatThrownBy(() ->
                ctx.consume(USER_ID, "submit_confirmed_transfer", now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("过期");
    }

    @Test
    void shouldRejectPrincipalMismatch() {
        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("amountFen", 50000L), 5);

        assertThatThrownBy(() ->
                ctx.consume("OTHER_USER", "submit_confirmed_transfer", Instant.now().plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("主体不匹配");
    }

    @Test
    void shouldRejectToolMismatch() {
        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("amountFen", 50000L), 5);

        assertThatThrownBy(() ->
                ctx.consume(USER_ID, "submit_confirmed_credit_repayment", Instant.now().plusSeconds(10)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("工具名不匹配");
    }

    @Test
    void shouldValidateConstraintsMatch() {
        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("amountFen", 50000L, "payeeId", "payee-001"), 5);

        // 约束匹配时不抛异常
        ctx.validateConstraints(Map.of("amountFen", 50000L, "payeeId", "payee-001"));
    }

    @Test
    void shouldRejectConstraintMismatch() {
        ConfirmationContext ctx = new ConfirmationContext(
                USER_ID, "submit_confirmed_transfer",
                Map.of("amountFen", 50000L), 5);

        assertThatThrownBy(() ->
                ctx.validateConstraints(Map.of("amountFen", 100000L)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldGenerateUniqueHandle() {
        ConfirmationContext ctx1 = new ConfirmationContext(USER_ID, "submit_confirmed_transfer",
                Map.of(), 5);
        ConfirmationContext ctx2 = new ConfirmationContext(USER_ID, "submit_confirmed_transfer",
                Map.of(), 5);
        assertThat(ctx1.getConfirmationHandle())
                .isNotEqualTo(ctx2.getConfirmationHandle());
    }
}
