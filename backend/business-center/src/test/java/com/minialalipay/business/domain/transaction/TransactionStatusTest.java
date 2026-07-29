package com.minialalipay.business.domain.transaction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionStatusTest {

    @Test
    void terminalStatusesCannotAcceptFurtherStateTransitions() {
        assertThat(TransactionStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(TransactionStatus.FAILED.isTerminal()).isTrue();
        assertThat(TransactionStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TransactionStatus.PROCESSING.isTerminal()).isFalse();
    }
}
