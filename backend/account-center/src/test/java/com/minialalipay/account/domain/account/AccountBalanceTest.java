package com.minialalipay.account.domain.account;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountBalanceTest {

    @Test
    void 收款确认增加可用余额且不产生冻结() {
        Instant now = Instant.parse("2026-08-04T08:00:00Z");
        AccountBalance balance = AccountBalance.zero("account-1", now);

        balance.credit(300L, now.plusSeconds(1));

        assertThat(balance.getAvailableFen()).isEqualTo(300L);
        assertThat(balance.getFrozenFen()).isZero();
    }

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void newBalanceStartsAtZero() {
        AccountBalance balance = AccountBalance.zero("01K1ACCOUNT0000000000000001", NOW);

        assertThat(balance.getAvailableFen()).isZero();
        assertThat(balance.getFrozenFen()).isZero();
        assertThat(balance.getTotalFen()).isZero();
        assertThat(balance.getVersion()).isZero();
    }

    @Test
    void freezeMovesAvailableToFrozenWithoutChangingTotal() {
        AccountBalance balance = new AccountBalance("account", 1_000L, 0L, 3L, NOW);

        balance.freeze(300L, NOW.plusSeconds(1));

        assertThat(balance.getAvailableFen()).isEqualTo(700L);
        assertThat(balance.getFrozenFen()).isEqualTo(300L);
        assertThat(balance.getTotalFen()).isEqualTo(1_000L);
    }

    @Test
    void rejectsFreezeWhenAvailableBalanceIsInsufficient() {
        AccountBalance balance = new AccountBalance("account", 100L, 0L, 0L, NOW);

        assertThatThrownBy(() -> balance.freeze(101L, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("账户可用余额不足");
    }

    @Test
    void confirmConsumesFrozenBalanceAndCancelRestoresAvailableBalance() {
        AccountBalance confirmed = new AccountBalance("account", 700L, 300L, 1L, NOW);
        AccountBalance cancelled = new AccountBalance("account", 700L, 300L, 1L, NOW);

        confirmed.confirm(300L, NOW.plusSeconds(1));
        cancelled.cancel(300L, NOW.plusSeconds(1));

        assertThat(confirmed.getAvailableFen()).isEqualTo(700L);
        assertThat(confirmed.getFrozenFen()).isZero();
        assertThat(confirmed.getTotalFen()).isEqualTo(700L);
        assertThat(cancelled.getAvailableFen()).isEqualTo(1_000L);
        assertThat(cancelled.getFrozenFen()).isZero();
    }

    @Test
    void rejectsNegativeReconstructedBalance() {
        assertThatThrownBy(() -> new AccountBalance("account", -1L, 0L, 0L, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("账户余额不得为负");
    }
}
