package com.minialalipay.account.domain.ledger;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerAccountTest {

    @Test
    void createsUserBalanceLiabilityAccount() {
        LedgerAccount account = LedgerAccount.userBalance("ledger", "user", "account",
                Instant.parse("2026-08-04T08:00:00Z"));

        assertThat(account.getOwnerType()).isEqualTo(LedgerOwnerType.USER);
        assertThat(account.getAccountType()).isEqualTo("USER_BALANCE_LIABILITY");
        assertThat(account.getAccountClass()).isEqualTo(LedgerAccountClass.LIABILITY);
        assertThat(account.getNormalDirection()).isEqualTo(LedgerDirection.CREDIT);
        assertThat(account.getAccountCode()).isEqualTo("USER_BALANCE_account");
    }
}
