package com.minialalipay.account.domain.ledger;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerVoucherTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void postsVoucherWhenActualDebitAndCreditAreBalanced() {
        LedgerVoucher voucher = LedgerVoucher.prepare("voucher", "transaction", "TRANSFER", 0,
                null, 500L, 500L, List.of(
                        new LedgerEntry(1L, "voucher", "transaction", "payer", LedgerDirection.DEBIT,
                                500L, 1, "付款", NOW),
                        new LedgerEntry(2L, "voucher", "transaction", "payee", LedgerDirection.CREDIT,
                                500L, 2, "收款", NOW)
                ), NOW);

        voucher.post(NOW.plusSeconds(1));

        assertThat(voucher.getStatus()).isEqualTo(LedgerVoucherStatus.POSTED);
        assertThat(voucher.getPostedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void rejectsUnbalancedEntries() {
        assertThatThrownBy(() -> LedgerVoucher.prepare("voucher", "transaction", "TRANSFER", 0,
                null, 500L, 500L, List.of(
                        new LedgerEntry(1L, "voucher", "transaction", "payer", LedgerDirection.DEBIT,
                                500L, 1, null, NOW),
                        new LedgerEntry(2L, "voucher", "transaction", "payee", LedgerDirection.CREDIT,
                                499L, 2, null, NOW)
                ), NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("账本凭证借贷不平");
    }

    @Test
    void rejectsDuplicateSequenceNumber() {
        assertThatThrownBy(() -> LedgerVoucher.prepare("voucher", "transaction", "TRANSFER", 0,
                null, 500L, 500L, List.of(
                        new LedgerEntry(1L, "voucher", "transaction", "payer", LedgerDirection.DEBIT,
                                500L, 1, null, NOW),
                        new LedgerEntry(2L, "voucher", "transaction", "payee", LedgerDirection.CREDIT,
                                500L, 1, null, NOW)
                ), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("凭证内分录序号不能重复");
    }

    @Test
    void reversalVoucherKeepsOriginalReferenceAndReason() {
        LedgerVoucher voucher = LedgerVoucher.prepareReversal("reversal", "transaction", "REVERSAL", 1,
                "original", LedgerReversalReason.RECONCILIATION, 500L, 500L, List.of(
                        new LedgerEntry(3L, "reversal", "transaction", "payee", LedgerDirection.DEBIT,
                                500L, 1, null, NOW),
                        new LedgerEntry(4L, "reversal", "transaction", "payer", LedgerDirection.CREDIT,
                                500L, 2, null, NOW)
                ), NOW);

        assertThat(voucher.getOriginalVoucherId()).isEqualTo("original");
        assertThat(voucher.getReversalReason()).isEqualTo(LedgerReversalReason.RECONCILIATION);
    }
}
