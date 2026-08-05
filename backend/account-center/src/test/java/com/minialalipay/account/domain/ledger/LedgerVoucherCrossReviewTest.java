package com.minialalipay.account.domain.ledger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T-12 交叉评审：账本凭证不变量与冲正约束测试。
 *
 * <p>验证 LedgerVoucher 的双重平衡校验、分录一致性、冲正引用约束和过账幂等。
 * 覆盖复式账本核心不变量：借贷必相等、分录不可变、冲正必须引用原凭证。</p>
 */
@DisplayName("T-12 账本凭证不变量交叉评审")
class LedgerVoucherCrossReviewTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    private List<LedgerEntry> balancedEntries(String voucherId, String txId, long amount) {
        return List.of(
                new LedgerEntry(1L, voucherId, txId, "payer-ledger", LedgerDirection.DEBIT,
                        amount, 1, "借方付款", NOW),
                new LedgerEntry(2L, voucherId, txId, "payee-ledger", LedgerDirection.CREDIT,
                        amount, 2, "贷方收款", NOW)
        );
    }

    @Test
    @DisplayName("准备阶段即校验借贷平衡，不平凭证无法创建")
    void prepareRejectsUnbalancedVoucher() {
        assertThatThrownBy(() -> LedgerVoucher.prepare("v-1", "tx-1", "TRANSFER", 0, null,
                500L, 600L, balancedEntries("v-1", "tx-1", 500L), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("借贷不平");
    }

    @Test
    @DisplayName("实际借贷合计必须与声明合计全部相等")
    void actualTotalsMustMatchDeclaredTotals() {
        // 声明合计 500/500，但实际分录合计 500/500 → 通过
        LedgerVoucher valid = LedgerVoucher.prepare("v-1", "tx-1", "TRANSFER", 0, null,
                500L, 500L, balancedEntries("v-1", "tx-1", 500L), NOW);
        assertThat(valid.getTotalDebitFen()).isEqualTo(500L);
        assertThat(valid.getTotalCreditFen()).isEqualTo(500L);
    }

    @Test
    @DisplayName("凭证至少包含两条分录")
    void voucherMustHaveAtLeastTwoEntries() {
        assertThatThrownBy(() -> LedgerVoucher.prepare("v-1", "tx-1", "TRANSFER", 0, null,
                0L, 0L, List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少包含两条分录");
    }

    @Test
    @DisplayName("分录序号不能重复")
    void duplicateSequenceNumberThrows() {
        List<LedgerEntry> duplicates = List.of(
                new LedgerEntry(1L, "v-1", "tx-1", "a", LedgerDirection.DEBIT, 500L, 1, null, NOW),
                new LedgerEntry(2L, "v-1", "tx-1", "b", LedgerDirection.CREDIT, 500L, 1, null, NOW)
        );
        assertThatThrownBy(() -> LedgerVoucher.prepare("v-1", "tx-1", "TRANSFER", 0, null,
                500L, 500L, duplicates, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("序号不能重复");
    }

    @Test
    @DisplayName("分录与凭证标识不一致时抛出异常")
    void entryVoucherIdMismatchThrows() {
        List<LedgerEntry> mismatch = List.of(
                new LedgerEntry(1L, "wrong-voucher", "tx-1", "a", LedgerDirection.DEBIT, 500L, 1, null, NOW),
                new LedgerEntry(2L, "v-1", "tx-1", "b", LedgerDirection.CREDIT, 500L, 2, null, NOW)
        );
        assertThatThrownBy(() -> LedgerVoucher.prepare("v-1", "tx-1", "TRANSFER", 0, null,
                500L, 500L, mismatch, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分录与凭证标识不一致");
    }

    @Test
    @DisplayName("PREPARED → POSTED 状态流转，过账后不可重复过账（幂等返回）")
    void postedVoucherIsIdempotent() {
        LedgerVoucher voucher = LedgerVoucher.prepare("v-1", "tx-1", "TRANSFER", 0, null,
                500L, 500L, balancedEntries("v-1", "tx-1", 500L), NOW);
        assertThat(voucher.getStatus()).isEqualTo(LedgerVoucherStatus.PREPARED);
        assertThat(voucher.getPostedAt()).isNull();

        voucher.post(NOW.plusSeconds(1));
        assertThat(voucher.getStatus()).isEqualTo(LedgerVoucherStatus.POSTED);
        assertThat(voucher.getPostedAt()).isEqualTo(NOW.plusSeconds(1));

        // 重复过账幂等返回
        voucher.post(NOW.plusSeconds(2));
        assertThat(voucher.getStatus()).isEqualTo(LedgerVoucherStatus.POSTED);
    }

    @Test
    @DisplayName("冲正凭证必须引用原凭证并说明原因")
    void reversalMustReferenceOriginalAndHaveReason() {
        // 冲正序号 > 0 但无原凭证 ID → 拒绝
        assertThatThrownBy(() -> LedgerVoucher.prepareReversal("v-2", "tx-1", "TRANSFER", 1,
                null, LedgerReversalReason.BUSINESS_REFUND, 500L, 500L,
                balancedEntries("v-2", "tx-1", 500L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("冲正凭证必须引用原凭证");

        // 冲正序号 > 0 但无原因 → 拒绝
        assertThatThrownBy(() -> LedgerVoucher.prepareReversal("v-2", "tx-1", "TRANSFER", 1,
                "v-1", null, 500L, 500L,
                balancedEntries("v-2", "tx-1", 500L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("冲正凭证必须引用原凭证");
    }

    @Test
    @DisplayName("原始凭证不能携带冲正引用")
    void originalVoucherCannotCarryReversalReference() {
        assertThatThrownBy(() -> LedgerVoucher.prepare("v-1", "tx-1", "TRANSFER", 0,
                "v-0", 500L, 500L, balancedEntries("v-1", "tx-1", 500L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("原始凭证不能携带冲正引用");
    }

    @Test
    @DisplayName("合法冲正凭证可创建并过账")
    void validReversalVoucherCanBePreparedAndPosted() {
        LedgerVoucher reversal = LedgerVoucher.prepareReversal("v-2", "tx-1", "TRANSFER", 1,
                "v-1", LedgerReversalReason.BUSINESS_REFUND, 500L, 500L,
                balancedEntries("v-2", "tx-1", 500L), NOW);
        assertThat(reversal.getReversalNo()).isEqualTo(1);
        assertThat(reversal.getOriginalVoucherId()).isEqualTo("v-1");
        assertThat(reversal.getReversalReason()).isEqualTo(LedgerReversalReason.BUSINESS_REFUND);
        assertThat(reversal.getStatus()).isEqualTo(LedgerVoucherStatus.PREPARED);

        reversal.post(NOW.plusSeconds(1));
        assertThat(reversal.getStatus()).isEqualTo(LedgerVoucherStatus.POSTED);
    }

    @Test
    @DisplayName("声明合计必须为正且借贷相等")
    void zeroOrNegativeDeclaredTotalsThrows() {
        // 声明合计为 0 但分录金额不为 0 → 借贷不平
        assertThatThrownBy(() -> LedgerVoucher.prepare("v-1", "tx-1", "TRANSFER", 0, null,
                0L, 0L, balancedEntries("v-1", "tx-1", 500L), NOW))
                .isInstanceOf(IllegalStateException.class);
    }
}
