package com.minialalipay.account.application.tcc;

import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.ledger.LedgerAccount;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.ledger.LedgerVoucher;
import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证信用支付账本不会错误复用付款余额科目或普通账本分支。 */
class CreditPayLedgerTccApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void tryCreatesCreditReceivableToPayeeBalanceVoucher() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        LedgerAccountRepository ledgerAccounts = mock(LedgerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        CreditPayLedgerTccApplicationService service = new CreditPayLedgerTccApplicationService(
                branches, ledgers, ledgerAccounts, accounts);
        var command = command();
        LedgerAccount receivable = LedgerAccount.creditReceivable("credit-receivable-ledger", "credit-account-1", NOW);
        LedgerAccount payeeBalance = LedgerAccount.userBalance("payee-balance-ledger", "payee-user-1", "payee-account-1", NOW);

        when(branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.CREDIT_PAY_LEDGER,
                command.voucherId())).thenReturn(Optional.empty());
        when(ledgers.find(command.transactionId(), "CREDIT_PAY", 0)).thenReturn(Optional.empty());
        when(ledgerAccounts.findCreditReceivableByCreditAccountId(command.creditAccountId()))
                .thenReturn(Optional.of(receivable));
        when(accounts.findById(command.payeeAccountId()))
                .thenReturn(Optional.of(Account.open(command.payeeAccountId(), "payee-user-1", "payee-registration", NOW)));
        when(ledgerAccounts.findUserBalanceByUserId("payee-user-1")).thenReturn(Optional.of(payeeBalance));
        when(branches.updateLedgerBranch(any(TccBranch.class), eq(0L))).thenReturn(true);

        TccBranch result = service.tryLedger(command, NOW);

        ArgumentCaptor<LedgerVoucher> voucher = ArgumentCaptor.forClass(LedgerVoucher.class);
        verify(ledgers).savePrepared(voucher.capture());
        assertThat(result.getBranchType()).isEqualTo(TccBranchType.CREDIT_PAY_LEDGER);
        assertThat(result.getStatus()).isEqualTo(TccBranchStatus.TRIED);
        assertThat(voucher.getValue().getVoucherType()).isEqualTo("CREDIT_PAY");
        assertThat(voucher.getValue().getEntries()).extracting(entry -> entry.ledgerAccountId())
                .containsExactly("credit-receivable-ledger", "payee-balance-ledger");
        assertThat(voucher.getValue().getEntries()).extracting(entry -> entry.direction())
                .containsExactly(LedgerDirection.DEBIT, LedgerDirection.CREDIT);
    }

    @Test
    void cancelBeforeTryCreatesCreditLedgerEmptyRollbackBarrier() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        CreditPayLedgerTccApplicationService service = new CreditPayLedgerTccApplicationService(
                branches, mock(LedgerRepository.class), mock(LedgerAccountRepository.class), mock(AccountRepository.class));
        var command = command();
        when(branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.CREDIT_PAY_LEDGER,
                command.voucherId())).thenReturn(Optional.empty());

        TccBranch result = service.cancelLedger(command, NOW);

        assertThat(result.getBranchType()).isEqualTo(TccBranchType.CREDIT_PAY_LEDGER);
        assertThat(result.getStatus()).isEqualTo(TccBranchStatus.CANCELLED);
        verify(branches).createLedgerBranch(result);
        verify(branches, never()).updateLedgerBranch(any(), any(Long.class));
    }

    private static CreditPayLedgerTccApplicationService.CreditPayLedgerCommand command() {
        return new CreditPayLedgerTccApplicationService.CreditPayLedgerCommand(
                "xid-credit-ledger-1", "transaction-1", "credit-account-1", "payee-account-1",
                12_000L, "voucher-1", 101L, 102L, "event-1", "0123456789abcdef0123456789abcdef");
    }
}
