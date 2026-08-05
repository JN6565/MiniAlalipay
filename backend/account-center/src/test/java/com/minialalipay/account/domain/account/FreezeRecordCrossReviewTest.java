package com.minialalipay.account.domain.account;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-12 交叉评审：冻结记录状态机与幂等测试。
 *
 * <p>验证 FreezeRecord 的 FROZEN→CONFIRMED/RELEASED 单向流转、
 * 重复同向幂等返回、反向回调拒绝。覆盖空回滚和防悬挂场景。</p>
 */
@DisplayName("T-12 冻结记录状态机交叉评审")
class FreezeRecordCrossReviewTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    private FreezeRecord createFrozen() {
        return FreezeRecord.create("freeze-1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 500L, "branch-xid-1", NOW);
    }

    @Test
    @DisplayName("FROZEN → CONFIRMED：确认后状态不可逆")
    void frozenToConfirmedIsTerminal() {
        FreezeRecord record = createFrozen();
        assertThat(record.getStatus()).isEqualTo(FreezeStatus.FROZEN);

        record.confirm(NOW.plusSeconds(1));
        assertThat(record.getStatus()).isEqualTo(FreezeStatus.CONFIRMED);

        // 重复确认幂等返回
        record.confirm(NOW.plusSeconds(2));
        assertThat(record.getStatus()).isEqualTo(FreezeStatus.CONFIRMED);
    }

    @Test
    @DisplayName("FROZEN → RELEASED：释放后状态不可逆")
    void frozenToReleasedIsTerminal() {
        FreezeRecord record = createFrozen();

        record.cancel(NOW.plusSeconds(1));
        assertThat(record.getStatus()).isEqualTo(FreezeStatus.RELEASED);

        // 重复释放幂等返回
        record.cancel(NOW.plusSeconds(2));
        assertThat(record.getStatus()).isEqualTo(FreezeStatus.RELEASED);
    }

    @Test
    @DisplayName("CONFIRMED 后不可释放（反向拒绝）")
    void confirmedCannotBeCancelled() {
        FreezeRecord record = createFrozen();
        record.confirm(NOW.plusSeconds(1));

        assertThatThrownBy(() -> record.cancel(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已确认的冻结记录不能释放");
    }

    @Test
    @DisplayName("RELEASED 后不可确认（防悬挂）")
    void releasedCannotBeConfirmed() {
        FreezeRecord record = createFrozen();
        record.cancel(NOW.plusSeconds(1));

        assertThatThrownBy(() -> record.confirm(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已释放的冻结记录不能确认");
    }

    @Test
    @DisplayName("创建冻结记录时金额必须为正")
    void createWithZeroOrNegativeAmountThrows() {
        assertThatThrownBy(() -> FreezeRecord.create("f", "tx", "acc",
                FreezePurpose.TRANSFER_OUT, 0L, "xid", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FreezeRecord.create("f", "tx", "acc",
                FreezePurpose.TRANSFER_OUT, -1L, "xid", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("创建冻结记录时关键字段不可为空")
    void createWithBlankFieldsThrows() {
        assertThatThrownBy(() -> FreezeRecord.create("", "tx", "acc",
                FreezePurpose.TRANSFER_OUT, 100L, "xid", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FreezeRecord.create("f", "", "acc",
                FreezePurpose.TRANSFER_OUT, 100L, "xid", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FreezeRecord.create("f", "tx", "",
                FreezePurpose.TRANSFER_OUT, 100L, "xid", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FreezeRecord.create("f", "tx", "acc",
                FreezePurpose.TRANSFER_OUT, 100L, "", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("不同 FreezePurpose 隔离同一交易的冻结记录")
    void differentPurposesAreIndependent() {
        FreezeRecord transferFreeze = FreezeRecord.create("f1", "tx-1", "acc-1",
                FreezePurpose.TRANSFER_OUT, 300L, "xid-1", NOW);
        FreezeRecord repayFreeze = FreezeRecord.create("f2", "tx-1", "acc-1",
                FreezePurpose.CREDIT_REPAYMENT, 500L, "xid-2", NOW);

        // 同一交易同一账户不同用途可以各自独立流转
        transferFreeze.confirm(NOW.plusSeconds(1));
        repayFreeze.cancel(NOW.plusSeconds(2));

        assertThat(transferFreeze.getStatus()).isEqualTo(FreezeStatus.CONFIRMED);
        assertThat(repayFreeze.getStatus()).isEqualTo(FreezeStatus.RELEASED);
    }
}
