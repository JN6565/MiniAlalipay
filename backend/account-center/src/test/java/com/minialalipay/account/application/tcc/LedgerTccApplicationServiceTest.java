package com.minialalipay.account.application.tcc;

import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
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
}
