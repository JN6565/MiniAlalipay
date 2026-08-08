package com.minialalipay.account.domain.bankcard;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 银行卡注册聚合状态机测试。
 *
 * <p>状态机：REGISTERED → BOUND（绑卡成功）；BOUND → REGISTERED（解绑时释放，
 * 支持重绑）。越界流转必须抛 IllegalStateException 阻断。</p>
 */
class RegisteredCardTest {

    private static final String USER = "USER001";
    private static final String HOLDER = "张三";
    private static final String ID_CARD = "330106199001011234";
    private static final String PHONE = "13812345678";
    private static final String CARD_NUMBER = "6212261234567890123";

    /** 用字典首项银行信息构造本人 REGISTERED 注册记录。 */
    private static RegisteredCard newRegistered() {
        BankCardNumber.BankCardInfo info = BankCardNumber.getAllBinEntries().stream()
                .findFirst().orElseThrow();
        return RegisteredCard.register("REG_TEST", USER, info, CARD_NUMBER,
                HOLDER, ID_CARD, PHONE, Instant.now());
    }

    @Test
    void markBoundFromRegisteredSucceeds() {
        RegisteredCard card = newRegistered();

        card.markBound();

        assertThat(card.getStatus()).isEqualTo("BOUND");
    }

    @Test
    void releaseFromBoundReturnsRegistered() {
        RegisteredCard card = newRegistered();
        card.markBound();

        card.release();

        assertThat(card.getStatus()).isEqualTo("REGISTERED");
    }

    @Test
    void releaseThenMarkBoundRoundTripAllowed() {
        // 释放后允许再次绑定，这是重绑流程的领域基础
        RegisteredCard card = newRegistered();
        card.markBound();
        card.release();

        card.markBound();

        assertThat(card.getStatus()).isEqualTo("BOUND");
    }

    @Test
    void releaseFromRegisteredThrows() {
        RegisteredCard card = newRegistered();

        assertThatThrownBy(card::release)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOUND");
    }

    @Test
    void markBoundFromBoundThrows() {
        RegisteredCard card = newRegistered();
        card.markBound();

        assertThatThrownBy(card::markBound)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REGISTERED");
    }
}
