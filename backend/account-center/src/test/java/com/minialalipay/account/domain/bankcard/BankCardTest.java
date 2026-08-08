package com.minialalipay.account.domain.bankcard;

import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 银行卡聚合不变量测试：绑卡工厂掩码化、ACTIVE→UNBOUND 终态不可逆、
 * 已解绑卡禁止任何后续操作。
 */
class BankCardTest {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
    private static final BankCardNumber.BankCardInfo ICBC =
            new BankCardNumber.BankCardInfo("ICBC", "中国工商银行", BankCardType.DEBIT);
    private static final String VALID_CARD = BankCardNumberTest.withLuhnCheckDigit("621226123456789");

    private BankCard bindCard() {
        return BankCard.bind("CARD001", "USER001", "ACC001", ICBC, VALID_CARD,
                "张三", "330106199001011234", "13812345678", true, NOW);
    }

    @Test
    void bindStoresOnlyMaskedValuesAndBinLast4() {
        BankCard card = bindCard();
        assertThat(card.getCardBin()).isEqualTo("621226");
        assertThat(card.getCardLast4()).isEqualTo(VALID_CARD.substring(VALID_CARD.length() - 4));
        assertThat(card.getHolderMasked()).isEqualTo("张*");
        assertThat(card.getIdCardMasked()).isEqualTo("3301**********1234");
        assertThat(card.getPhoneMasked()).isEqualTo("138****5678");
        assertThat(card.getStatus()).isEqualTo(BankCardStatus.ACTIVE);
        assertThat(card.isDefault()).isTrue();
        assertThat(card.getVersion()).isZero();
    }

    @Test
    void unbindEntersTerminalState() {
        BankCard card = bindCard();
        card.unbind(NOW);
        assertThat(card.getStatus()).isEqualTo(BankCardStatus.UNBOUND);
        assertThat(card.isDefault()).isFalse();
        assertThat(card.getUnboundAt()).isEqualTo(NOW);
    }

    @Test
    void unbindTwiceRejectedAsTerminalState() {
        BankCard card = bindCard();
        card.unbind(NOW);
        assertThatThrownBy(() -> card.unbind(NOW))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).errorCode())
                .isEqualTo(BankCardErrorCode.BANK_CARD_ALREADY_UNBOUND);
    }

    @Test
    void markDefaultOnUnboundCardRejected() {
        BankCard card = bindCard();
        card.unbind(NOW);
        assertThatThrownBy(() -> card.markDefault(NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void clearDefaultOnUnboundCardRejected() {
        BankCard card = bindCard();
        card.unbind(NOW);
        assertThatThrownBy(() -> card.clearDefault(NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markAndClearDefaultUpdateTimestamp() {
        BankCard card = bindCard();
        Instant later = NOW.plusSeconds(60);
        card.clearDefault(later);
        assertThat(card.isDefault()).isFalse();
        card.markDefault(later);
        assertThat(card.isDefault()).isTrue();
        assertThat(card.getUpdatedAt()).isEqualTo(later);
    }
}
