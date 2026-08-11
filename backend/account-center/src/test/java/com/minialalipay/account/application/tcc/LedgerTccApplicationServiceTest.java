package com.minialalipay.account.application.tcc;

import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountStatus;
import com.minialalipay.account.domain.account.AccountType;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.ledger.LedgerAccount;
import com.minialalipay.account.domain.ledger.LedgerAccountClass;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.domain.ledger.LedgerOwnerType;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.ledger.LedgerVoucher;
import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * 验证账本 TCC Confirm 必须以数据库实际分录验平结果作为过账前置条件。
 */
class LedgerTccApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Test
    void 数据库分录借贷不平时拒绝过账且分支保持tried() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        LedgerTccApplicationService service = new LedgerTccApplicationService(
                branches,
                ledgers,
                mock(LedgerAccountRepository.class),
                mock(AccountRepository.class)
        );
        var command = command();
        TccBranch branch = TccBranch.initialize(
                command.xid(), TccBranchType.LEDGER, command.voucherId(),
                command.transactionId(), command.amountFen(), NOW);
        branch.markTried(NOW.plusSeconds(1));
        LedgerVoucher voucher = voucher(command);

        when(branches.findLedgerBranchForUpdate(command.xid(), command.voucherId()))
                .thenReturn(Optional.of(branch));
        when(ledgers.find(command.transactionId(), "TRANSFER", 0))
                .thenReturn(Optional.of(voucher));
        when(ledgers.summarizeEntries(command.voucherId()))
                .thenReturn(new LedgerRepository.LedgerTotals(command.amountFen(), command.amountFen() - 1));

        assertThatThrownBy(() -> service.confirmLedger(command, NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数据库账本分录借贷不平");

        assertThat(branch.getStatus()).isEqualTo(TccBranchStatus.TRIED);
        verify(ledgers, never()).postAndAppendOutbox(
                voucher, command.eventId(), command.traceId(), NOW.plusSeconds(2));
        verify(branches, never()).updateLedgerBranch(branch, branch.getBarrierVersion());
    }

    @Test
    void 银行卡外部出资账本只给收款用户生成余额贷方分录() {
        TccBranchRepository branches = mock(TccBranchRepository.class);
        LedgerRepository ledgers = mock(LedgerRepository.class);
        LedgerAccountRepository ledgerAccounts = mock(LedgerAccountRepository.class);
        AccountRepository accounts = mock(AccountRepository.class);
        LedgerTccApplicationService service = new LedgerTccApplicationService(
                branches, ledgers, ledgerAccounts, accounts);
        var command = externalCommand();

        when(branches.findLedgerBranchForUpdate(command.xid(), command.voucherId()))
                .thenReturn(Optional.empty());
        when(accounts.findById(command.payeeAccountId())).thenReturn(Optional.of(account("payee-account", "user-b")));
        when(ledgerAccounts.findSystemIssuance()).thenReturn(Optional.of(systemIssuance()));
        when(ledgerAccounts.findUserBalanceByUserId("user-b")).thenReturn(Optional.of(userLedger("payee-ledger", "user-b")));
        when(ledgers.find(command.transactionId(), "TRANSFER", 0)).thenReturn(Optional.empty());
        when(branches.updateLedgerBranch(any(TccBranch.class), eq(0L))).thenReturn(true);

        service.tryExternalFundingLedger(command, NOW);

        verify(ledgers).savePrepared(any(LedgerVoucher.class));
        verify(accounts, never()).findById("payer-account");
    }

    private static LedgerTccApplicationService.LedgerCommand command() {
        return new LedgerTccApplicationService.LedgerCommand(
                "xid-ledger-1", "transaction-1", "payer-account", "payee-account",
                100L, "voucher-1", 101L, 102L, "event-1",
                "0123456789abcdef0123456789abcdef");
    }

    private static LedgerVoucher voucher(LedgerTccApplicationService.LedgerCommand command) {
        List<LedgerEntry> entries = List.of(
                new LedgerEntry(
                        command.debitEntryId(), command.voucherId(), command.transactionId(),
                        "payer-ledger", LedgerDirection.DEBIT, command.amountFen(), 1,
                        "付款", NOW),
                new LedgerEntry(
                        command.creditEntryId(), command.voucherId(), command.transactionId(),
                        "payee-ledger", LedgerDirection.CREDIT, command.amountFen(), 2,
                        "收款", NOW)
        );
        return LedgerVoucher.prepare(
                command.voucherId(), command.transactionId(), "TRANSFER", 0, null,
                command.amountFen(), command.amountFen(), entries, NOW);
    }

    private static LedgerTccApplicationService.ExternalFundingLedgerCommand externalCommand() {
        return new LedgerTccApplicationService.ExternalFundingLedgerCommand(
                "xid-card-transfer-1", "transaction-card-1", "payee-account",
                100L, "voucher-card-1", 201L, 202L, "event-card-1",
                "0123456789abcdef0123456789abcdef");
    }

    private static Account account(String accountId, String userId) {
        return new Account(accountId, userId, "registration-" + userId, AccountType.PERSONAL, "CNY",
                AccountStatus.ACTIVE, 0L, NOW, NOW);
    }

    private static LedgerAccount userLedger(String ledgerAccountId, String userId) {
        return LedgerAccount.userBalance(ledgerAccountId, userId, "balance-" + userId, NOW);
    }

    private static LedgerAccount systemIssuance() {
        return new LedgerAccount("system-issuance", LedgerOwnerType.SYSTEM, "SYSTEM_ISSUANCE",
                "SYSTEM_ISSUANCE", "SYSTEM_ISSUANCE_EQUITY", LedgerAccountClass.EQUITY,
                LedgerDirection.CREDIT, "CNY", com.minialalipay.account.domain.ledger.LedgerAccountStatus.ACTIVE,
                NOW, NOW);
    }
}
