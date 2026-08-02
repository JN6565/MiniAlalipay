package com.minialalipay.business.domain.transaction;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionStatusTest {

    @Test
    void transactionStatusesMatchFundTransactionStateMachine() {
        assertThat(Arrays.stream(TransactionStatus.values()).map(Enum::name))
                .containsExactly(
                        "PROCESSING",
                        "COMPENSATING",
                        "MANUAL_REVIEW",
                        "SUCCESS",
                        "REVERSED",
                        "CANCELLED"
                );
    }

    @Test
    void statusHelperUsesDefinitiveOutcomeSemantics() {
        assertThat(Arrays.stream(TransactionStatus.class.getDeclaredMethods()).map(Method::getName))
                .contains("hasDefinitiveOutcome")
                .doesNotContain("isTerminal");
    }

    @Test
    void settledStatusesDoNotRequireBackgroundConvergence() {
        assertThat(Arrays.stream(TransactionStatus.values())
                .filter(TransactionStatus::hasDefinitiveOutcome)
                .map(Enum::name))
                .containsExactly("SUCCESS", "REVERSED", "CANCELLED");
    }
}
