package com.minialalipay.account.application.tcc;

import com.minialalipay.account.application.account.BalanceApplicationService;
import com.minialalipay.account.application.tcc.BalanceTccApplicationService.TccCommand;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.tcc.RollbackType;
import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BalanceTccApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");
    private static final TccCommand COMMAND = new TccCommand("xid-1", "tx-1", "account-1", 100L, "freeze-1");

    @Test
    void cancel先到时只建立empty屏障且不操作余额() {
        TccBranchRepository repository = mock(TccBranchRepository.class);
        BalanceApplicationService balances = mock(BalanceApplicationService.class);
        when(repository.findAccountBranchForUpdate("xid-1", TccBranchType.PAYER_BALANCE, "account-1"))
                .thenReturn(Optional.empty());
        BalanceTccApplicationService service = new BalanceTccApplicationService(repository, balances, mock(AccountRepository.class));

        TccBranch result = service.cancelPayer(COMMAND, NOW);

        assertThat(result.getStatus()).isEqualTo(TccBranchStatus.CANCELLED);
        assertThat(result.getRollbackType()).isEqualTo(RollbackType.EMPTY);
        verify(repository).createAccountBranch(any(TccBranch.class));
        verify(balances, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void 已有empty屏障时拒绝晚到付款try() {
        TccBranchRepository repository = mock(TccBranchRepository.class);
        BalanceApplicationService balances = mock(BalanceApplicationService.class);
        TccBranch cancelled = TccBranch.emptyRollback("xid-1", TccBranchType.PAYER_BALANCE,
                "account-1", "tx-1", 100L, NOW);
        when(repository.findAccountBranchForUpdate("xid-1", TccBranchType.PAYER_BALANCE, "account-1"))
                .thenReturn(Optional.of(cancelled));
        BalanceTccApplicationService service = new BalanceTccApplicationService(repository, balances, mock(AccountRepository.class));

        assertThatThrownBy(() -> service.tryPayer(COMMAND, NOW.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("晚到 Try");
        verify(balances, never()).freeze(any(), any(), any(), any(), any(Long.class), any(), any());
    }
}
