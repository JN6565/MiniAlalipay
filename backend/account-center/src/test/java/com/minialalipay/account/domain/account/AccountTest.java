package com.minialalipay.account.domain.account;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void opensPersonalCnyAccountWithZeroVersion() {
        Account account = Account.open("01K1ACCOUNT0000000000000001", "01K1USER0000000000000000001",
                "01K1REGISTER000000000000001", NOW);

        assertThat(account.getAccountType()).isEqualTo(AccountType.PERSONAL);
        assertThat(account.getCurrency()).isEqualTo("CNY");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getVersion()).isZero();
    }

    @Test
    void rejectsBlankOpeningIdentity() {
        assertThatThrownBy(() -> Account.open(" ", "user", "registration", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("账户 ID 不能为空");
    }
}
