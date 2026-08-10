package com.minialalipay.business.domain.transaction;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionTypeTest {

    @Test
    void transactionTypesMatchFundTransactionContract() {
        assertThat(Arrays.stream(TransactionType.values()).map(Enum::name))
                .containsExactly("TRANSFER", "QR_PAY", "CREDIT_PAY", "CREDIT_REPAY", "RECHARGE", "REFUND",
                        "BANK_CARD_RECHARGE", "BANK_CARD_WITHDRAW");
    }
}
