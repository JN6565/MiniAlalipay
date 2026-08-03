package com.minialalipay.account.domain.credit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CreditFreeze} 领域模型单元测试。
 */
@DisplayName("CreditFreeze 信用冻结记录")
class CreditFreezeTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final String CREDIT_FREEZE_ID = "freeze-01";
    private static final String TRANSACTION_ID = "tx-01";
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-01";
    private static final String BRANCH_XID = "branch-01";

    @Nested
    @DisplayName("创建冻结记录")
    class Create {

        @Test
        @DisplayName("创建后状态=FROZEN，金额正确")
        void shouldCreateFrozenRecord() {
            CreditFreeze freeze = new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    100_000L, BRANCH_XID, NOW
            );

            assertThat(freeze.getStatus()).isEqualTo(CreditFreezeStatus.FROZEN);
            assertThat(freeze.getAmountFen()).isEqualTo(100_000L);
            assertThat(freeze.getCreditFreezeId()).isEqualTo(CREDIT_FREEZE_ID);
            assertThat(freeze.getTransactionId()).isEqualTo(TRANSACTION_ID);
            assertThat(freeze.getCreditAccountId()).isEqualTo(CREDIT_ACCOUNT_ID);
            assertThat(freeze.getBranchXid()).isEqualTo(BRANCH_XID);
            assertThat(freeze.getVersion()).isZero();
            assertThat(freeze.getCreatedAt()).isEqualTo(NOW);
            assertThat(freeze.getUpdatedAt()).isEqualTo(NOW);
        }
    }

    @Nested
    @DisplayName("金额范围校验")
    class AmountValidation {

        @Test
        @DisplayName("金额<1 抛异常")
        void shouldThrowWhenAmountLessThanOne() {
            assertThatThrownBy(() -> new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    0L, BRANCH_XID, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("冻结金额必须在 1~5000000 分范围内");
        }

        @Test
        @DisplayName("金额>5000000 抛异常")
        void shouldThrowWhenAmountExceedsMax() {
            assertThatThrownBy(() -> new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    5_000_001L, BRANCH_XID, NOW
            ))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("冻结金额必须在 1~5000000 分范围内");
        }
    }

    @Nested
    @DisplayName("confirm 确认冻结")
    class Confirm {

        @Test
        @DisplayName("FROZEN→CONFIRMED")
        void shouldTransitionToConfirmed() {
            CreditFreeze freeze = new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    100_000L, BRANCH_XID, NOW
            );

            freeze.confirm(NOW);

            assertThat(freeze.getStatus()).isEqualTo(CreditFreezeStatus.CONFIRMED);
        }

        @Test
        @DisplayName("非 FROZEN 状态确认抛异常")
        void shouldThrowWhenNotFrozen() {
            CreditFreeze freeze = new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    100_000L, BRANCH_XID, NOW
            );
            freeze.confirm(NOW);

            assertThatThrownBy(() -> freeze.confirm(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅 FROZEN 状态可确认");
        }
    }

    @Nested
    @DisplayName("release 释放冻结")
    class Release {

        @Test
        @DisplayName("FROZEN→RELEASED")
        void shouldTransitionToReleased() {
            CreditFreeze freeze = new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    100_000L, BRANCH_XID, NOW
            );

            freeze.release(NOW);

            assertThat(freeze.getStatus()).isEqualTo(CreditFreezeStatus.RELEASED);
        }

        @Test
        @DisplayName("非 FROZEN 状态释放抛异常")
        void shouldThrowWhenNotFrozen() {
            CreditFreeze freeze = new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    100_000L, BRANCH_XID, NOW
            );
            freeze.release(NOW);

            assertThatThrownBy(() -> freeze.release(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("仅 FROZEN 状态可释放");
        }
    }

    @Nested
    @DisplayName("终态不可回退")
    class TerminalState {

        @Test
        @DisplayName("CONFIRMED 后 confirm 抛异常")
        void shouldThrowWhenConfirmConfirmed() {
            CreditFreeze freeze = new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    100_000L, BRANCH_XID, NOW
            );
            freeze.confirm(NOW);

            assertThatThrownBy(() -> freeze.confirm(NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("CONFIRMED 后 release 抛异常")
        void shouldThrowWhenReleaseConfirmed() {
            CreditFreeze freeze = new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    100_000L, BRANCH_XID, NOW
            );
            freeze.confirm(NOW);

            assertThatThrownBy(() -> freeze.release(NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("RELEASED 后 confirm 抛异常")
        void shouldThrowWhenConfirmReleased() {
            CreditFreeze freeze = new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    100_000L, BRANCH_XID, NOW
            );
            freeze.release(NOW);

            assertThatThrownBy(() -> freeze.confirm(NOW))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("RELEASED 后 release 抛异常")
        void shouldThrowWhenReleaseReleased() {
            CreditFreeze freeze = new CreditFreeze(
                    CREDIT_FREEZE_ID, TRANSACTION_ID, CREDIT_ACCOUNT_ID,
                    100_000L, BRANCH_XID, NOW
            );
            freeze.release(NOW);

            assertThatThrownBy(() -> freeze.release(NOW))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
