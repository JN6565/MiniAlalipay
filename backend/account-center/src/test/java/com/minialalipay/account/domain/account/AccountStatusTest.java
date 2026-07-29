package com.minialalipay.account.domain.account;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountStatusTest {

    @Test
    void onlyActiveAccountAllowsDebit() {
        assertThat(AccountStatus.ACTIVE.allowsDebit()).isTrue();
        assertThat(AccountStatus.FROZEN.allowsDebit()).isFalse();
        assertThat(AccountStatus.CLOSED.allowsDebit()).isFalse();
    }
}
