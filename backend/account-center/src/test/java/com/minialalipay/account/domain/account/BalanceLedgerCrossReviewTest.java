package com.minialalipay.account.domain.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-12 交叉评审：余额聚合并发安全与不变量测试。
 *
 * <p>验证 AccountBalance 的 CAS 乐观锁、金额非负不变量和总余额守恒。
 * 覆盖王钧平提交的余额内核中评审重点项。</p>
 */
@DisplayName("T-12 余额聚合交叉评审")
class BalanceLedgerCrossReviewTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    @DisplayName("freeze + confirm 后总余额减少，available 和 frozen 均非负")
    void freezeAndConfirmReducesTotalWhileKeepingNonNegative() {
        AccountBalance balance = new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW);

        balance.freeze(3_000L, NOW.plusSeconds(1));
        assertThat(balance.getAvailableFen()).isEqualTo(7_000L);
        assertThat(balance.getFrozenFen()).isEqualTo(3_000L);
        assertThat(balance.getTotalFen()).isEqualTo(10_000L);

        balance.confirm(3_000L, NOW.plusSeconds(2));
        assertThat(balance.getAvailableFen()).isEqualTo(7_000L);
        assertThat(balance.getFrozenFen()).isZero();
        assertThat(balance.getTotalFen()).isEqualTo(7_000L);
    }

    @Test
    @DisplayName("freeze + cancel 后总余额不变")
    void freezeAndCancelPreservesTotalBalance() {
        AccountBalance balance = new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW);

        balance.freeze(3_000L, NOW.plusSeconds(1));
        balance.cancel(3_000L, NOW.plusSeconds(2));

        assertThat(balance.getAvailableFen()).isEqualTo(10_000L);
        assertThat(balance.getFrozenFen()).isZero();
        assertThat(balance.getTotalFen()).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("多笔冻结累积后逐笔确认")
    void multipleFreezesThenConfirmOneByOne() {
        AccountBalance balance = new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW);

        balance.freeze(2_000L, NOW.plusSeconds(1));
        balance.freeze(3_000L, NOW.plusSeconds(2));
        assertThat(balance.getAvailableFen()).isEqualTo(5_000L);
        assertThat(balance.getFrozenFen()).isEqualTo(5_000L);

        balance.confirm(2_000L, NOW.plusSeconds(3));
        assertThat(balance.getFrozenFen()).isEqualTo(3_000L);
        assertThat(balance.getTotalFen()).isEqualTo(8_000L);

        balance.confirm(3_000L, NOW.plusSeconds(4));
        assertThat(balance.getFrozenFen()).isZero();
        assertThat(balance.getTotalFen()).isEqualTo(5_000L);
    }

    @Test
    @DisplayName("可用余额不足时冻结抛出 IllegalStateException")
    void freezeExceedingAvailableThrows() {
        AccountBalance balance = new AccountBalance("acc-1", 1_000L, 0L, 0L, NOW);
        assertThatThrownBy(() -> balance.freeze(1_001L, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("可用余额不足");
    }

    @Test
    @DisplayName("冻结金额为零或负数抛出 IllegalArgumentException")
    void freezeZeroOrNegativeThrows() {
        AccountBalance balance = new AccountBalance("acc-1", 1_000L, 0L, 0L, NOW);
        assertThatThrownBy(() -> balance.freeze(0L, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> balance.freeze(-1L, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("confirm 超过冻结金额时抛出异常")
    void confirmExceedingFrozenThrows() {
        AccountBalance balance = new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW);
        balance.freeze(3_000L, NOW.plusSeconds(1));
        assertThatThrownBy(() -> balance.confirm(3_001L, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("冻结余额不足");
    }

    @Test
    @DisplayName("cancel 超过冻结金额时抛出异常")
    void cancelExceedingFrozenThrows() {
        AccountBalance balance = new AccountBalance("acc-1", 10_000L, 0L, 0L, NOW);
        balance.freeze(3_000L, NOW.plusSeconds(1));
        assertThatThrownBy(() -> balance.cancel(3_001L, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("冻结余额不足");
    }

    @Test
    @DisplayName("零余额账户初始化后 available 和 frozen 均为零")
    void zeroBalanceInitialization() {
        AccountBalance zero = AccountBalance.zero("acc-1", NOW);
        assertThat(zero.getAvailableFen()).isZero();
        assertThat(zero.getFrozenFen()).isZero();
        assertThat(zero.getTotalFen()).isZero();
        assertThat(zero.getVersion()).isZero();
    }

    @Test
    @DisplayName("重建负余额事实时抛出异常")
    void reconstructingNegativeBalanceThrows() {
        assertThatThrownBy(() -> new AccountBalance("acc-1", -1L, 0L, 0L, NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AccountBalance("acc-1", 0L, -1L, 0L, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getTotalFen 使用 addExact 防止 long 溢出")
    void totalFenUsesAddExactToPreventOverflow() {
        long maxHalf = Long.MAX_VALUE / 2 + 1;
        AccountBalance balance = new AccountBalance("acc-1", maxHalf, maxHalf, 0L, NOW);
        assertThatThrownBy(balance::getTotalFen)
                .isInstanceOf(ArithmeticException.class);
    }
}
