package com.minialalipay.business.application.recovery;

import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证服务重启后的恢复扫描只接管原交易，不创建新的资金主单或分支标识。
 */
class TransactionRecoveryScannerTest {

    @Test
    void 扫描到超时交易时将持久化交易原样交给协调器() {
        BusinessStore store = mock(BusinessStore.class);
        TccCoordinatorPort coordinator = mock(TccCoordinatorPort.class);
        FundTransaction transaction = transaction();
        when(store.findRecoverable(any(Instant.class), eq(100)))
                .thenReturn(List.of(new BusinessStore.FundTransactionRecord(transaction, null)));

        new TransactionRecoveryScanner(store, coordinator).recoverTimedOutTransactions();

        verify(coordinator).startOrResume(transaction);
    }

    @Test
    void 没有超时交易时不触发协调器() {
        BusinessStore store = mock(BusinessStore.class);
        TccCoordinatorPort coordinator = mock(TccCoordinatorPort.class);
        when(store.findRecoverable(any(Instant.class), eq(100))).thenReturn(List.of());

        new TransactionRecoveryScanner(store, coordinator).recoverTimedOutTransactions();

        verifyNoInteractions(coordinator);
    }

    private static FundTransaction transaction() {
        return FundTransaction.accept(
                "01K1TX0002GH3JK4MN5PQRSTV",
                TransactionType.TRANSFER,
                SourceType.TRANSFER_DRAFT,
                "01K1DRAFT02GH3JK4MN5PQRSTV",
                "payer-user",
                "payer-account",
                "payee-account",
                FundingSource.BALANCE,
                100L,
                "idem-key-00000001",
                "LOW",
                "0123456789abcdef0123456789abcdef",
                Instant.parse("2026-08-04T08:00:00Z")
        );
    }
}
