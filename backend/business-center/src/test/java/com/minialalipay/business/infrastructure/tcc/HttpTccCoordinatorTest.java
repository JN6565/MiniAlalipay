package com.minialalipay.business.infrastructure.tcc;

import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionStatus;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.business.infrastructure.security.SecureMaterialService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpTccCoordinatorTest {
    @Test
    void confirm超时时保持processing并安排恢复而不执行cancel() {
        BusinessStore store = mock(BusinessStore.class);
        FundTransaction transaction = transaction();
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        expectPost(server, "/internal/v1/tcc/balance/payer/try");
        expectPost(server, "/internal/v1/tcc/balance/payee/try");
        expectPost(server, "/internal/v1/tcc/ledger/try");
        server.expect(once(), requestTo("http://account/internal/v1/tcc/balance/payer/confirm"))
                .andExpect(method(HttpMethod.POST)).andRespond(withServerError());
        HttpTccCoordinator coordinator = new HttpTccCoordinator(store, new SecureMaterialService(), builder, "http://account");

        coordinator.startOrResume(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PROCESSING);
        verify(store).updateTccGlobal(eq("tcc:" + transaction.getTransactionId()), eq("COMMITTING"),
                eq("{\"result\":\"UNKNOWN\"}"), any(Instant.class), any(Instant.class));
        server.verify();
    }

    @Test
    void try失败后执行空回滚并在事实一致时发布cancelled() {
        BusinessStore store = mock(BusinessStore.class);
        when(store.updateTransaction(any(), any(Long.class), any(), any())).thenReturn(true);
        FundTransaction transaction = transaction();
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://account/internal/v1/tcc/balance/payer/try"))
                .andExpect(method(HttpMethod.POST)).andRespond(withServerError());
        expectPost(server, "/internal/v1/tcc/ledger/cancel");
        expectPost(server, "/internal/v1/tcc/balance/payee/cancel");
        expectPost(server, "/internal/v1/tcc/balance/payer/cancel");
        server.expect(once(), requestTo("http://account/internal/v1/transaction-facts/" + transaction.getTransactionId()))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"successConsistent\":false,\"cancelConsistent\":true,\"accountsConfirmed\":false,\"ledgerConfirmed\":false,\"freezeConfirmed\":false,\"ledgerPosted\":false,\"accountsCancelled\":true,\"ledgerCancelled\":true,\"noActiveFreeze\":true}", MediaType.APPLICATION_JSON));
        HttpTccCoordinator coordinator = new HttpTccCoordinator(store, new SecureMaterialService(), builder, "http://account");

        coordinator.startOrResume(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
        server.verify();
    }

    @Test
    void confirm完成但资金事实不一致时记录差异并原子转人工工单() {
        BusinessStore store = mock(BusinessStore.class);
        FundTransaction transaction = transaction();
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        expectPost(server, "/internal/v1/tcc/balance/payer/try");
        expectPost(server, "/internal/v1/tcc/balance/payee/try");
        expectPost(server, "/internal/v1/tcc/ledger/try");
        expectPost(server, "/internal/v1/tcc/balance/payer/confirm");
        expectPost(server, "/internal/v1/tcc/balance/payee/confirm");
        expectPost(server, "/internal/v1/tcc/ledger/confirm");
        server.expect(once(), requestTo("http://account/internal/v1/transaction-facts/" + transaction.getTransactionId()))
                .andExpect(method(HttpMethod.GET)).andRespond(withSuccess("{\"successConsistent\":false,\"cancelConsistent\":false,\"accountsConfirmed\":true,\"ledgerConfirmed\":true,\"freezeConfirmed\":true,\"ledgerPosted\":false,\"accountsCancelled\":false,\"ledgerCancelled\":false,\"noActiveFreeze\":false}", MediaType.APPLICATION_JSON));
        expectPost(server, "/internal/v1/reconciliation-diffs");
        HttpTccCoordinator coordinator = new HttpTccCoordinator(store, new SecureMaterialService(), builder, "http://account");

        coordinator.startOrResume(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.MANUAL_REVIEW);
        verify(store).moveToManualReview(eq(transaction), eq(0L), eq("tcc:" + transaction.getTransactionId()),
                any(), any(), eq("SUCCESS_FACT_MISMATCH"), any(Instant.class));
        server.verify();
    }

    private static void expectPost(MockRestServiceServer server, String path) {
        server.expect(once(), requestTo("http://account" + path)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
    }
    private static FundTransaction transaction() {
        return FundTransaction.accept("01K1TX0002GH3JK4MN5PQRSTV", TransactionType.TRANSFER,
                SourceType.TRANSFER_DRAFT, "01K1DRAFT02GH3JK4MN5PQRSTV", "payer-user",
                "payer-account", "payee-account", FundingSource.BALANCE, 100L, "idem-key-00000001",
                "LOW", "0123456789abcdef0123456789abcdef", Instant.parse("2026-08-04T08:00:00Z"));
    }
}
