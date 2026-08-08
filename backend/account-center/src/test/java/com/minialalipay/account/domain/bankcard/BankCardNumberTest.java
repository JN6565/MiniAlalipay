package com.minialalipay.account.domain.bankcard;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 银行卡号校验与 BIN 识别测试：覆盖 Luhn 防误输、长度边界、
 * 分隔符规范化与「暂不支持银行」拒绝路径。
 */
public class BankCardNumberTest {

    /** 为给定卡号主体计算 Luhn 校验位，生成合法测试卡号；供其他包测试复用。 */
    public static String withLuhnCheckDigit(String base) {
        int sum = 0;
        boolean doubleNext = true;
        for (int i = base.length() - 1; i >= 0; i--) {
            int digit = base.charAt(i) - '0';
            if (doubleNext) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleNext = !doubleNext;
        }
        return base + ((10 - sum % 10) % 10);
    }

    @Test
    void normalizeRemovesGroupingSeparators() {
        assertThat(BankCardNumber.normalize("6212 2612-3456 7890")).isEqualTo("6212261234567890");
        assertThat(BankCardNumber.normalize(null)).isEmpty();
    }

    @Test
    void luhnValidCardPasses() {
        String card = withLuhnCheckDigit("621226123456789");
        assertThat(BankCardNumber.isValid(card)).isTrue();
    }

    @Test
    void luhnInvalidCardRejected() {
        String card = withLuhnCheckDigit("621226123456789");
        // 篡改末位校验位，Luhn 和不再被 10 整除
        char wrong = card.charAt(card.length() - 1) == '0' ? '1' : '0';
        assertThat(BankCardNumber.isValid(card.substring(0, card.length() - 1) + wrong)).isFalse();
    }

    @Test
    void lengthOutOfRangeRejected() {
        assertThat(BankCardNumber.isValid("12345")).isFalse();
        assertThat(BankCardNumber.isValid(withLuhnCheckDigit("62122612345678901234"))).isFalse();
        assertThat(BankCardNumber.isValid(null)).isFalse();
    }

    @Test
    void nonDigitRejected() {
        assertThat(BankCardNumber.isValid("62122612345678a0")).isFalse();
    }

    @Test
    void knownBinIdentifiesBankAndType() {
        Optional<BankCardNumber.BankCardInfo> info =
                BankCardNumber.identify(withLuhnCheckDigit("621226123456789"));
        assertThat(info).isPresent();
        assertThat(info.get().bankCode()).isEqualTo("ICBC");
        assertThat(info.get().bankName()).isEqualTo("中国工商银行");
        assertThat(info.get().cardType()).isEqualTo(BankCardType.DEBIT);
    }

    @Test
    void creditBinIdentifiedAsCredit() {
        Optional<BankCardNumber.BankCardInfo> info =
                BankCardNumber.identify(withLuhnCheckDigit("622575123456789"));
        assertThat(info).isPresent();
        assertThat(info.get().cardType()).isEqualTo(BankCardType.CREDIT);
    }

    @Test
    void unknownBinReturnsEmpty() {
        assertThat(BankCardNumber.identify(withLuhnCheckDigit("999999123456789"))).isEmpty();
        assertThat(BankCardNumber.identify("12345")).isEmpty();
    }
}
