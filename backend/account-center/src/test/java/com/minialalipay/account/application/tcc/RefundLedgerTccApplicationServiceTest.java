package com.minialalipay.account.application.tcc;

import com.minialalipay.account.application.tcc.RefundLedgerTccApplicationService.RefundLedgerCommand;
import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.ledger.LedgerAccount;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.ledger.LedgerVoucher;
import com.minialalipay.account.domain.ledger.LedgerVoucherStatus;
import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证退款账本不会错误复用普通转账账本分支或余额付款科目。
 *
 * <p>信用退款贷方必须是信用应收资产科目，余额退款贷方必须是原付款人余额科目；
 * 借方始终是原收款方（退款发起人）余额负债科目。</p>
 */
class RefundLedgerTccApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-06T12:00:00Z");
    private static final long AMOUNT_FEN = 12_000L;

    @Test
    void tryCreatesRefundVoucherForCreditRefund() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        LedgerAccountRepository ledgerAccounts = mock(LedgerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        RefundLedgerTccApplicationService service = new RefundLedgerTccApplicationService(
                branches, ledgers, ledgerAccounts, accounts);
        RefundLedgerCommand command = creditCommand();
        LedgerAccount merchantBalance = LedgerAccount.userBalance("merchant-balance-ledger", "merchant-user-1", "merchant-account-1", NOW);
        LedgerAccount receivable = LedgerAccount.creditReceivable("credit-receivable-ledger", "credit-account-1", NOW);

        when(branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId())).thenReturn(Optional.empty());
        when(ledgers.find(command.transactionId(), "REFUND", 0)).thenReturn(Optional.empty());
        when(accounts.findById(command.merchantAccountId()))
                .thenReturn(Optional.of(Account.open(command.merchantAccountId(), "merchant-user-1", "merchant-registration", NOW)));
        when(ledgerAccounts.findUserBalanceByUserId("merchant-user-1")).thenReturn(Optional.of(merchantBalance));
        when(ledgerAccounts.findCreditReceivableByCreditAccountId(command.creditAccountId()))
                .thenReturn(Optional.of(receivable));
        when(branches.updateLedgerBranch(any(TccBranch.class), eq(0L))).thenReturn(true);

        TccBranch result = service.tryLedger(command, NOW);

        ArgumentCaptor<LedgerVoucher> voucher = ArgumentCaptor.forClass(LedgerVoucher.class);
        verify(ledgers).savePrepared(voucher.capture());
        assertThat(result.getBranchType()).isEqualTo(TccBranchType.REFUND_LEDGER);
        assertThat(result.getStatus()).isEqualTo(TccBranchStatus.TRIED);
        assertThat(voucher.getValue().getVoucherType()).isEqualTo("REFUND");
        assertThat(voucher.getValue().getEntries()).extracting(entry -> entry.ledgerAccountId())
                .containsExactly("merchant-balance-ledger", "credit-receivable-ledger");
        assertThat(voucher.getValue().getEntries()).extracting(entry -> entry.direction())
                .containsExactly(LedgerDirection.DEBIT, LedgerDirection.CREDIT);
    }

    @Test
    void tryCreatesRefundVoucherForBalanceRefund() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        LedgerAccountRepository ledgerAccounts = mock(LedgerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        RefundLedgerTccApplicationService service = new RefundLedgerTccApplicationService(
                branches, ledgers, ledgerAccounts, accounts);
        RefundLedgerCommand command = balanceCommand();
        LedgerAccount merchantBalance = LedgerAccount.userBalance("merchant-balance-ledger", "merchant-user-1", "merchant-account-1", NOW);
        LedgerAccount payerBalance = LedgerAccount.userBalance("payer-balance-ledger", "payer-user-1", "payer-account-1", NOW);

        when(branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId())).thenReturn(Optional.empty());
        when(ledgers.find(command.transactionId(), "REFUND", 0)).thenReturn(Optional.empty());
        when(accounts.findById(command.merchantAccountId()))
                .thenReturn(Optional.of(Account.open(command.merchantAccountId(), "merchant-user-1", "merchant-registration", NOW)));
        when(ledgerAccounts.findUserBalanceByUserId("merchant-user-1")).thenReturn(Optional.of(merchantBalance));
        when(accounts.findById(command.payerAccountId()))
                .thenReturn(Optional.of(Account.open(command.payerAccountId(), "payer-user-1", "payer-registration", NOW)));
        when(ledgerAccounts.findUserBalanceByUserId("payer-user-1")).thenReturn(Optional.of(payerBalance));
        when(branches.updateLedgerBranch(any(TccBranch.class), eq(0L))).thenReturn(true);

        TccBranch result = service.tryLedger(command, NOW);

        ArgumentCaptor<LedgerVoucher> voucher = ArgumentCaptor.forClass(LedgerVoucher.class);
        verify(ledgers).savePrepared(voucher.capture());
        assertThat(voucher.getValue().getEntries()).extracting(entry -> entry.ledgerAccountId())
                .containsExactly("merchant-balance-ledger", "payer-balance-ledger");
    }

    @Test
    void cancelBeforeTryCreatesEmptyRollbackBarrier() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        RefundLedgerTccApplicationService service = new RefundLedgerTccApplicationService(
                branches, mock(LedgerRepository.class), mock(LedgerAccountRepository.class), mock(AccountRepository.class));
        RefundLedgerCommand command = creditCommand();
        when(branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId())).thenReturn(Optional.empty());

        TccBranch result = service.cancelLedger(command, NOW);

        assertThat(result.getBranchType()).isEqualTo(TccBranchType.REFUND_LEDGER);
        assertThat(result.getStatus()).isEqualTo(TccBranchStatus.CANCELLED);
        verify(branches).createLedgerBranch(result);
        verify(branches, never()).updateLedgerBranch(any(), any(Long.class));
    }

    @Test
    void confirmPostsBalancedRefundVoucher() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        LedgerAccountRepository ledgerAccounts = mock(LedgerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        RefundLedgerTccApplicationService service = new RefundLedgerTccApplicationService(
                branches, ledgers, ledgerAccounts, accounts);
        RefundLedgerCommand command = creditCommand();
        LedgerAccount merchantBalance = LedgerAccount.userBalance("merchant-balance-ledger", "merchant-user-1", "merchant-account-1", NOW);
        LedgerAccount receivable = LedgerAccount.creditReceivable("credit-receivable-ledger", "credit-account-1", NOW);
        TccBranch branch = TccBranch.initialize(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId(), command.transactionId(), command.amountFen(), NOW);
        branch.markTried(NOW);
        LedgerVoucher prepared = LedgerVoucher.prepare(command.voucherId(), command.transactionId(), "REFUND", 0, null,
                AMOUNT_FEN, AMOUNT_FEN, List.of(
                        new LedgerEntry(command.debitEntryId(), command.voucherId(), command.transactionId(),
                                merchantBalance.getLedgerAccountId(), LedgerDirection.DEBIT, AMOUNT_FEN, 1, "受控退款：减少原收款方余额", NOW),
                        new LedgerEntry(command.creditEntryId(), command.voucherId(), command.transactionId(),
                                receivable.getLedgerAccountId(), LedgerDirection.CREDIT, AMOUNT_FEN, 2, "受控退款：核销信用应收资产", NOW)),
                NOW);

        when(branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId())).thenReturn(Optional.of(branch));
        when(ledgers.find(command.transactionId(), "REFUND", 0)).thenReturn(Optional.of(prepared));
        when(ledgers.summarizeEntries(command.voucherId()))
                .thenReturn(new LedgerRepository.LedgerTotals(AMOUNT_FEN, AMOUNT_FEN));
        when(ledgers.postAndAppendOutbox(any(LedgerVoucher.class), eq(command.eventId()), eq(command.traceId()), any(Instant.class)))
                .thenReturn(true);
        when(branches.updateLedgerBranch(any(TccBranch.class), eq(1L))).thenReturn(true);

        TccBranch result = service.confirmLedger(command, NOW);

        assertThat(result.getStatus()).isEqualTo(TccBranchStatus.CONFIRMED);
        verify(ledgers).postAndAppendOutbox(any(LedgerVoucher.class), eq(command.eventId()), eq(command.traceId()), any(Instant.class));
    }

    @Test
    void confirmWithUnbalancedEntriesRejects() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        RefundLedgerTccApplicationService service = new RefundLedgerTccApplicationService(
                branches, ledgers, mock(LedgerAccountRepository.class), mock(AccountRepository.class));
        RefundLedgerCommand command = creditCommand();
        TccBranch branch = TccBranch.initialize(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId(), command.transactionId(), command.amountFen(), NOW);
        branch.markTried(NOW);
        LedgerAccount merchantBalance = LedgerAccount.userBalance("merchant-balance-ledger", "merchant-user-1", "merchant-account-1", NOW);
        LedgerAccount receivable = LedgerAccount.creditReceivable("credit-receivable-ledger", "credit-account-1", NOW);
        LedgerVoucher prepared = LedgerVoucher.prepare(command.voucherId(), command.transactionId(), "REFUND", 0, null,
                AMOUNT_FEN, AMOUNT_FEN, List.of(
                        new LedgerEntry(command.debitEntryId(), command.voucherId(), command.transactionId(),
                                merchantBalance.getLedgerAccountId(), LedgerDirection.DEBIT, AMOUNT_FEN, 1, "受控退款：减少原收款方余额", NOW),
                        new LedgerEntry(command.creditEntryId(), command.voucherId(), command.transactionId(),
                                receivable.getLedgerAccountId(), LedgerDirection.CREDIT, AMOUNT_FEN, 2, "受控退款：核销信用应收资产", NOW)),
                NOW);

        when(branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId())).thenReturn(Optional.of(branch));
        when(ledgers.find(command.transactionId(), "REFUND", 0)).thenReturn(Optional.of(prepared));
        when(ledgers.summarizeEntries(command.voucherId()))
                .thenReturn(new LedgerRepository.LedgerTotals(AMOUNT_FEN, AMOUNT_FEN - 1));

        assertThatThrownBy(() -> service.confirmLedger(command, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("借贷不平");
    }

    @Test
    void tryWithCancelledBranchRejectsLateTry() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        RefundLedgerTccApplicationService service = new RefundLedgerTccApplicationService(
                branches, mock(LedgerRepository.class), mock(LedgerAccountRepository.class), mock(AccountRepository.class));
        RefundLedgerCommand command = creditCommand();
        TccBranch cancelled = TccBranch.emptyRollback(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId(), command.transactionId(), command.amountFen(), NOW);
        when(branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId())).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> service.tryLedger(command, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("拒绝晚到 Try");
    }

    private static RefundLedgerCommand creditCommand() {
        return new RefundLedgerCommand("xid-refund-1", "refund-tx-1", "merchant-account-1",
                null, "credit-account-1", AMOUNT_FEN, "voucher-refund-1", 201L, 202L,
                "event-refund-1", "0123456789abcdef0123456789abcdef");
    }

    private static RefundLedgerCommand balanceCommand() {
        return new RefundLedgerCommand("xid-refund-1", "refund-tx-1", "merchant-account-1",
                "payer-account-1", null, AMOUNT_FEN, "voucher-refund-1", 201L, 202L,
                "event-refund-1", "0123456789abcdef0123456789abcdef");
    }
}
