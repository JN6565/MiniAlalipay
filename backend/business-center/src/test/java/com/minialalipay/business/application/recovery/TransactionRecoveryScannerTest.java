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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 恢复扫描器人工态复核测试。
 *
 * <p>人工态交易必须被定期复核收敛，不得永远停留在人工审核中；
 * 超过重试上限的真实异常交易保持人工态等待人工介入。</p>
 */
class TransactionRecoveryScannerTest {

    @Test
    void 扫描到超时交易时将持久化交易原样交给协调器() {
        BusinessStore store = mock(BusinessStore.class);
        TccCoordinatorPort coordinator = mock(TccCoordinatorPort.class);
        FundTransaction transaction = timedOutTransaction();
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

    @Test
    void 人工态花呗交易被复核以重新核验资金事实() {
        BusinessStore store = mock(BusinessStore.class);
        TccCoordinatorPort coordinator = mock(TccCoordinatorPort.class);
        FundTransaction transaction = manualReviewTransaction();
        when(store.findManualReviewRecheckable(any(Instant.class), eq(100)))
                .thenReturn(List.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        when(store.getTccRetryCount(transaction.getTransactionId())).thenReturn(0);

        new TransactionRecoveryScanner(store, coordinator).recheckManualReviewTransactions();

        verify(coordinator).recheckManualReview(transaction);
        // 复核必须走专用路径，不得误用在途扫描的接管入口
        verify(coordinator, never()).startOrResume(any());
    }

    @Test
    void 超过重试上限的人工态交易保持人工态不再复核() {
        BusinessStore store = mock(BusinessStore.class);
        TccCoordinatorPort coordinator = mock(TccCoordinatorPort.class);
        FundTransaction transaction = manualReviewTransaction();
        when(store.findManualReviewRecheckable(any(Instant.class), eq(100)))
                .thenReturn(List.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        when(store.getTccRetryCount(transaction.getTransactionId())).thenReturn(10);

        new TransactionRecoveryScanner(store, coordinator).recheckManualReviewTransactions();

        verifyNoInteractions(coordinator);
    }

    private static FundTransaction timedOutTransaction() {
        return FundTransaction.accept("01K1TX0002GH3JK4MN5PQRSTV", TransactionType.TRANSFER,
                SourceType.TRANSFER_DRAFT, "01K1DRAFT02GH3JK4MN5PQRSTV", "payer-user",
                "payer-account", "payee-account", FundingSource.BALANCE, 100L,
                "idem-key-00000001", "LOW", "0123456789abcdef0123456789abcdef",
                Instant.parse("2026-08-04T08:00:00Z"));
    }

    private static FundTransaction manualReviewTransaction() {
        FundTransaction transaction = FundTransaction.accept("01K1TXC002GH3JK4MN5PQRSTV", TransactionType.CREDIT_PAY,
                SourceType.QR_PAY_ORDER, "01K1QR0002GH3JK4MN5PQRSTV", "payer-user",
                "payer-account", "payee-account", FundingSource.MINI_CREDIT, 2200L,
                "idem-key-00000001", "LOW", "0123456789abcdef0123456789abcdef",
                Instant.parse("2026-08-04T08:00:00Z"));
        transaction.requireManualReview(Instant.parse("2026-08-04T08:01:00Z"));
        return transaction;
    }
}
