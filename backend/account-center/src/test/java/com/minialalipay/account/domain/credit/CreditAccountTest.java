package com.minialalipay.account.domain.credit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CreditAccount} 领域模型单元测试。
 */
@DisplayName("CreditAccount 信用账户聚合根")
class CreditAccountTest {

    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");
    private static final String CREDIT_ACCOUNT_ID = "credit-acc-01";
    private static final String USER_ID = "user-01";

    @Nested
    @DisplayName("开户")
    class OpenAccount {

        @Test
        @DisplayName("开户后总额度=500000，已用=0，冻结=0，可用=500000，状态=ACTIVE")
        void shouldCreateActiveAccountWithFixedLimit() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);

            assertThat(account.getTotalLimitFen()).isEqualTo(CreditAccount.FIXED_TOTAL_LIMIT_FEN);
            assertThat(account.getTotalLimitFen()).isEqualTo(500_000L);
            assertThat(account.getUsedFen()).isZero();
            assertThat(account.getFrozenFen()).isZero();
            assertThat(account.getAvailableFen()).isEqualTo(500_000L);
            assertThat(account.getStatus()).isEqualTo(CreditAccountStatus.ACTIVE);
            assertThat(account.getCreditAccountId()).isEqualTo(CREDIT_ACCOUNT_ID);
            assertThat(account.getUserId()).isEqualTo(USER_ID);
            assertThat(account.getVersion()).isZero();
            assertThat(account.getCreatedAt()).isEqualTo(NOW);
            assertThat(account.getUpdatedAt()).isEqualTo(NOW);
            assertThat(account.getSuspendReason()).isNull();
        }
    }

    @Nested
    @DisplayName("freeze 冻结额度")
    class Freeze {

        @Test
        @DisplayName("正常冻结后 frozen 增加，available 减少")
        void shouldIncreaseFrozenAndDecreaseAvailable() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);

            account.freeze(100_000L, NOW);

            assertThat(account.getFrozenFen()).isEqualTo(100_000L);
            assertThat(account.getAvailableFen()).isEqualTo(400_000L);
            assertThat(account.getUsedFen()).isZero();
        }

        @Test
        @DisplayName("非 ACTIVE 状态冻结抛异常")
        void shouldThrowWhenStatusIsNotActive() {
            CreditAccount account = new CreditAccount(
                    CREDIT_ACCOUNT_ID, USER_ID, 500_000L, 0L, 0L,
                    CreditAccountStatus.SUSPENDED, "test", 0L, NOW, NOW
            );

            assertThatThrownBy(() -> account.freeze(100L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("信用账户当前不可用");
        }

        @Test
        @DisplayName("超过可用额度冻结抛异常")
        void shouldThrowWhenAmountExceedsAvailable() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);

            assertThatThrownBy(() -> account.freeze(500_001L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("可用信用额度不足");
        }

        @Test
        @DisplayName("金额<=0 冻结抛异常")
        void shouldThrowWhenAmountNotPositive() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);

            assertThatThrownBy(() -> account.freeze(0L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("冻结金额必须为正");

            assertThatThrownBy(() -> account.freeze(-1L, NOW))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("冻结金额必须为正");
        }
    }

    @Nested
    @DisplayName("confirmFreeze 冻结转已用")
    class ConfirmFreeze {

        @Test
        @DisplayName("冻结转已用，frozen 减少 used 增加")
        void shouldTransferFrozenToUsed() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);
            account.freeze(100_000L, NOW);

            account.confirmFreeze(60_000L, NOW);

            assertThat(account.getFrozenFen()).isEqualTo(40_000L);
            assertThat(account.getUsedFen()).isEqualTo(60_000L);
            assertThat(account.getAvailableFen()).isEqualTo(400_000L);
        }

        @Test
        @DisplayName("超过冻结额度确认抛异常")
        void shouldThrowWhenAmountExceedsFrozen() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);
            account.freeze(100_000L, NOW);

            assertThatThrownBy(() -> account.confirmFreeze(100_001L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("冻结额度不足以确认");
        }
    }

    @Nested
    @DisplayName("releaseFreeze 释放冻结")
    class ReleaseFreeze {

        @Test
        @DisplayName("释放冻结，frozen 减少")
        void shouldDecreaseFrozen() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);
            account.freeze(100_000L, NOW);

            account.releaseFreeze(40_000L, NOW);

            assertThat(account.getFrozenFen()).isEqualTo(60_000L);
            assertThat(account.getAvailableFen()).isEqualTo(440_000L);
        }

        @Test
        @DisplayName("超过冻结额度释放抛异常")
        void shouldThrowWhenAmountExceedsFrozen() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);
            account.freeze(100_000L, NOW);

            assertThatThrownBy(() -> account.releaseFreeze(100_001L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("冻结额度不足以释放");
        }
    }

    @Nested
    @DisplayName("restoreByRepayment 还款恢复")
    class RestoreByRepayment {

        @Test
        @DisplayName("还款恢复，used 减少")
        void shouldDecreaseUsed() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);
            account.freeze(100_000L, NOW);
            account.confirmFreeze(100_000L, NOW);

            account.restoreByRepayment(40_000L, NOW);

            assertThat(account.getUsedFen()).isEqualTo(60_000L);
            assertThat(account.getAvailableFen()).isEqualTo(440_000L);
        }

        @Test
        @DisplayName("超过已用额度恢复抛异常")
        void shouldThrowWhenAmountExceedsUsed() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);
            account.freeze(100_000L, NOW);
            account.confirmFreeze(100_000L, NOW);

            assertThatThrownBy(() -> account.restoreByRepayment(100_001L, NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已用额度不足以恢复");
        }
    }

    @Nested
    @DisplayName("suspend 暂停账户")
    class Suspend {

        @Test
        @DisplayName("ACTIVE→SUSPENDED")
        void shouldSuspendFromActive() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);

            account.suspend("逾期暂停", NOW);

            assertThat(account.getStatus()).isEqualTo(CreditAccountStatus.SUSPENDED);
            assertThat(account.getSuspendReason()).isEqualTo("逾期暂停");
        }

        @Test
        @DisplayName("CLOSED 状态暂停抛异常")
        void shouldThrowWhenSuspendClosedAccount() {
            CreditAccount account = new CreditAccount(
                    CREDIT_ACCOUNT_ID, USER_ID, 500_000L, 0L, 0L,
                    CreditAccountStatus.CLOSED, null, 0L, NOW, NOW
            );

            assertThatThrownBy(() -> account.suspend("test", NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已关闭的信用账户不可暂停");
        }
    }

    @Nested
    @DisplayName("activate 恢复账户")
    class Activate {

        @Test
        @DisplayName("SUSPENDED→ACTIVE")
        void shouldActivateFromSuspended() {
            CreditAccount account = new CreditAccount(
                    CREDIT_ACCOUNT_ID, USER_ID, 500_000L, 0L, 0L,
                    CreditAccountStatus.SUSPENDED, "test", 0L, NOW, NOW
            );

            account.activate(NOW);

            assertThat(account.getStatus()).isEqualTo(CreditAccountStatus.ACTIVE);
            assertThat(account.getSuspendReason()).isNull();
        }

        @Test
        @DisplayName("CLOSED 状态恢复抛异常")
        void shouldThrowWhenActivateClosedAccount() {
            CreditAccount account = new CreditAccount(
                    CREDIT_ACCOUNT_ID, USER_ID, 500_000L, 0L, 0L,
                    CreditAccountStatus.CLOSED, null, 0L, NOW, NOW
            );

            assertThatThrownBy(() -> account.activate(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已关闭的信用账户不可恢复");
        }
    }

    @Nested
    @DisplayName("close 关闭账户")
    class Close {

        @Test
        @DisplayName("used=0,frozen=0 时可关闭")
        void shouldCloseWhenNoOutstanding() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);

            account.close(NOW);

            assertThat(account.getStatus()).isEqualTo(CreditAccountStatus.CLOSED);
        }

        @Test
        @DisplayName("有未结清额度（used>0）关闭抛异常")
        void shouldThrowWhenUsedIsNotZero() {
            CreditAccount account = new CreditAccount(
                    CREDIT_ACCOUNT_ID, USER_ID, 500_000L, 100_000L, 0L,
                    CreditAccountStatus.ACTIVE, null, 0L, NOW, NOW
            );

            assertThatThrownBy(() -> account.close(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("存在未结清额度，不可关闭");
        }

        @Test
        @DisplayName("有未结清额度（frozen>0）关闭抛异常")
        void shouldThrowWhenFrozenIsNotZero() {
            CreditAccount account = new CreditAccount(
                    CREDIT_ACCOUNT_ID, USER_ID, 500_000L, 0L, 100_000L,
                    CreditAccountStatus.ACTIVE, null, 0L, NOW, NOW
            );

            assertThatThrownBy(() -> account.close(NOW))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("存在未结清额度，不可关闭");
        }
    }

    @Nested
    @DisplayName("allowsCreditPay 信用支付判断")
    class AllowsCreditPay {

        @Test
        @DisplayName("ACTIVE 状态返回 true")
        void shouldReturnTrueForActive() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);
            assertThat(account.allowsCreditPay()).isTrue();
        }

        @Test
        @DisplayName("SUSPENDED 状态返回 false")
        void shouldReturnFalseForSuspended() {
            CreditAccount account = new CreditAccount(
                    CREDIT_ACCOUNT_ID, USER_ID, 500_000L, 0L, 0L,
                    CreditAccountStatus.SUSPENDED, "test", 0L, NOW, NOW
            );
            assertThat(account.allowsCreditPay()).isFalse();
        }

        @Test
        @DisplayName("CLOSED 状态返回 false")
        void shouldReturnFalseForClosed() {
            CreditAccount account = new CreditAccount(
                    CREDIT_ACCOUNT_ID, USER_ID, 500_000L, 0L, 0L,
                    CreditAccountStatus.CLOSED, null, 0L, NOW, NOW
            );
            assertThat(account.allowsCreditPay()).isFalse();
        }
    }

    @Nested
    @DisplayName("不变量校验")
    class Invariant {

        @Test
        @DisplayName("total = available + used + frozen 始终成立")
        void shouldMaintainInvariantAfterOperations() {
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);

            account.freeze(100_000L, NOW);
            assertThat(account.getTotalLimitFen())
                    .isEqualTo(account.getAvailableFen() + account.getUsedFen() + account.getFrozenFen());

            account.confirmFreeze(60_000L, NOW);
            assertThat(account.getTotalLimitFen())
                    .isEqualTo(account.getAvailableFen() + account.getUsedFen() + account.getFrozenFen());

            account.releaseFreeze(20_000L, NOW);
            assertThat(account.getTotalLimitFen())
                    .isEqualTo(account.getAvailableFen() + account.getUsedFen() + account.getFrozenFen());

            account.restoreByRepayment(30_000L, NOW);
            assertThat(account.getTotalLimitFen())
                    .isEqualTo(account.getAvailableFen() + account.getUsedFen() + account.getFrozenFen());
        }
    }

    @Nested
    @DisplayName("完整生命周期")
    class Lifecycle {

        @Test
        @DisplayName("开户→freeze→confirmFreeze→restoreByRepayment→close")
        void shouldCompleteFullLifecycle() {
            // 开户
            CreditAccount account = new CreditAccount(CREDIT_ACCOUNT_ID, USER_ID, NOW);
            assertThat(account.getStatus()).isEqualTo(CreditAccountStatus.ACTIVE);
            assertThat(account.allowsCreditPay()).isTrue();

            // freeze
            account.freeze(200_000L, NOW);
            assertThat(account.getFrozenFen()).isEqualTo(200_000L);
            assertThat(account.getAvailableFen()).isEqualTo(300_000L);

            // confirmFreeze
            account.confirmFreeze(200_000L, NOW);
            assertThat(account.getFrozenFen()).isZero();
            assertThat(account.getUsedFen()).isEqualTo(200_000L);

            // restoreByRepayment
            account.restoreByRepayment(200_000L, NOW);
            assertThat(account.getUsedFen()).isZero();
            assertThat(account.getAvailableFen()).isEqualTo(500_000L);

            // close
            account.close(NOW);
            assertThat(account.getStatus()).isEqualTo(CreditAccountStatus.CLOSED);
            assertThat(account.allowsCreditPay()).isFalse();
        }
    }
}
