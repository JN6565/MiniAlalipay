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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpTccCoordinatorTest {
    @Test
    void confirm失败后使用稳定分支键重试并在事实一致时发布success() {
        BusinessStore store = mock(BusinessStore.class);
        FundTransaction transaction = transaction();
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var secure = new SecureMaterialService();

        expectPayerTryWithStableKey(server, transaction, secure);
        expectPost(server, "/internal/v1/tcc/balance/payee/try");
        expectPost(server, "/internal/v1/tcc/ledger/try");
        server.expect(once(), requestTo("http://account/internal/v1/tcc/balance/payer/confirm"))
                .andExpect(method(HttpMethod.POST)).andRespond(withServerError());

        // 模拟恢复扫描接管：重新执行 Try 和 Confirm，参与者依赖稳定键保证不重复占用资金。
        expectPayerTryWithStableKey(server, transaction, secure);
        expectPost(server, "/internal/v1/tcc/balance/payee/try");
        expectPost(server, "/internal/v1/tcc/ledger/try");
        expectPost(server, "/internal/v1/tcc/balance/payer/confirm");
        expectPost(server, "/internal/v1/tcc/balance/payee/confirm");
        expectPost(server, "/internal/v1/tcc/ledger/confirm");
        expectFacts(server, transaction, successFacts());

        new HttpTccCoordinator(store, secure, builder, "http://account").startOrResume(transaction);
        new HttpTccCoordinator(store, secure, builder, "http://account").startOrResume(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        verify(store).finalizeTransaction(eq(transaction), eq(0L),
                eq("tcc:" + transaction.getTransactionId()), eq("SUCCESS"),
                any(), any(Instant.class));
        server.verify();
    }

    @Test
    void cancel失败后恢复扫描按逆序重试并在事实一致时发布cancelled() {
        BusinessStore store = mock(BusinessStore.class);
        FundTransaction transaction = transaction();
        transaction.startCompensating(Instant.parse("2026-08-04T08:01:00Z"));
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(once(), requestTo("http://account/internal/v1/tcc/ledger/cancel"))
                .andExpect(method(HttpMethod.POST)).andRespond(withServerError());
        expectPost(server, "/internal/v1/tcc/ledger/cancel");
        expectPost(server, "/internal/v1/tcc/balance/payee/cancel");
        expectPost(server, "/internal/v1/tcc/balance/payer/cancel");
        expectFacts(server, transaction, cancelledFacts());

        var secure = new SecureMaterialService();
        HttpTccCoordinator coordinator = new HttpTccCoordinator(store, secure, builder, "http://account");
        coordinator.startOrResume(transaction);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPENSATING);
        verify(store).updateTccGlobal(eq("tcc:" + transaction.getTransactionId()), eq("ROLLING_BACK"),
                eq("{\"result\":\"UNKNOWN\"}"), any(Instant.class), any(Instant.class));
        coordinator.startOrResume(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
        verify(store).finalizeTransaction(eq(transaction), eq(1L),
                eq("tcc:" + transaction.getTransactionId()), eq("CANCELLED"),
                any(), any(Instant.class));
        server.verify();
    }

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

    @Test
    void 信用支付冻结额度并增加收款余额后发布成功() {
        BusinessStore store = mock(BusinessStore.class);
        FundTransaction transaction = creditTransaction();
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://account/internal/v1/credit-accounts/by-user/payer-user"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"creditAccountId\":\"01K1CREDIT02GH3JK4MN5PQRST\",\"userId\":\"payer-user\",\"status\":\"ACTIVE\",\"version\":0}", MediaType.APPLICATION_JSON));
        expectPost(server, "/internal/v1/tcc/credit-pay/try");
        expectPost(server, "/internal/v1/tcc/balance/payee/try");
        expectPost(server, "/internal/v1/tcc/credit-ledger/try");
        expectPost(server, "/internal/v1/tcc/credit-pay/confirm");
        expectPost(server, "/internal/v1/tcc/balance/payee/confirm");
        expectPost(server, "/internal/v1/tcc/credit-ledger/confirm");
        expectFacts(server, transaction, successFacts());

        new HttpTccCoordinator(store, new SecureMaterialService(), builder, "http://account")
                .startOrResume(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        verify(store).finalizeTransaction(eq(transaction), eq(0L),
                eq("tcc:" + transaction.getTransactionId()), eq("SUCCESS"),
                any(), any(Instant.class));
        server.verify();
    }

    @Test
    void 信用退款只冻结商户并走creditRefund与refundLedger专用分支() {
        BusinessStore store = mock(BusinessStore.class);
        FundTransaction transaction = creditRefundTransaction();
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        FundTransaction original = creditPayTransaction();
        when(store.findTransaction(transaction.getRelatedTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(original, null)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 只冻结退款发起人（原收款方）余额，不冻结原付款人
        expectPost(server, "/internal/v1/tcc/balance/payer/try");
        server.expect(once(), requestTo("http://account/internal/v1/tcc/credit-refund/try"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.originalTransactionId").value(transaction.getRelatedTransactionId()))
                .andExpect(jsonPath("$.merchantAccountId").value(MERCHANT_ACCOUNT_ID))
                .andRespond(withSuccess());
        server.expect(once(), requestTo("http://account/internal/v1/tcc/refund-ledger/try"))
                .andExpect(method(HttpMethod.POST))
                // 信用账户由原信用支付交易的付款账户稳定派生
                .andExpect(jsonPath("$.creditAccountId").value(CREDIT_ACCOUNT_ID))
                .andExpect(jsonPath("$.merchantAccountId").value(MERCHANT_ACCOUNT_ID))
                .andRespond(withSuccess());
        expectPost(server, "/internal/v1/tcc/balance/payer/confirm");
        expectPost(server, "/internal/v1/tcc/credit-refund/confirm");
        expectPost(server, "/internal/v1/tcc/refund-ledger/confirm");

        new HttpTccCoordinator(store, new SecureMaterialService(), builder, "http://account")
                .startOrResume(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        verify(store).finalizeTransaction(eq(transaction), eq(0L),
                eq("tcc:" + transaction.getTransactionId()), eq("SUCCESS"),
                any(), any(Instant.class));
        server.verify();
    }

    @Test
    void 余额退款仍只冻结商户并贷记原付款人余额() {
        BusinessStore store = mock(BusinessStore.class);
        FundTransaction transaction = balanceRefundTransaction();
        when(store.findTransaction(transaction.getTransactionId()))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        expectPost(server, "/internal/v1/tcc/balance/payer/try");
        expectPost(server, "/internal/v1/tcc/balance/payee/try");
        server.expect(once(), requestTo("http://account/internal/v1/tcc/refund-ledger/try"))
                .andExpect(method(HttpMethod.POST))
                // 余额退款不携带信用账户，贷方为原付款人余额科目
                .andExpect(jsonPath("$.creditAccountId").doesNotExist())
                .andExpect(jsonPath("$.payerAccountId").value(PAYER_ACCOUNT_ID))
                .andRespond(withSuccess());
        expectPost(server, "/internal/v1/tcc/balance/payer/confirm");
        expectPost(server, "/internal/v1/tcc/balance/payee/confirm");
        expectPost(server, "/internal/v1/tcc/refund-ledger/confirm");

        new HttpTccCoordinator(store, new SecureMaterialService(), builder, "http://account")
                .startOrResume(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        server.verify();
    }

    private static FundTransaction creditPayTransaction() {
        return FundTransaction.accept(CREDIT_PAY_TX, TransactionType.CREDIT_PAY, SourceType.QR_PAY_ORDER,
                QR_ORDER_ID, "payer-user", CREDIT_ACCOUNT_ID, MERCHANT_ACCOUNT_ID,
                FundingSource.MINI_CREDIT, 100L, "idem-key-00000001", "LOW",
                "0123456789abcdef0123456789abcdef", Instant.parse("2026-08-04T08:00:00Z"));
    }

    private static FundTransaction creditRefundTransaction() {
        return FundTransaction.acceptRefund(REFUND_TX, SourceType.REFUND_ORDER, REFUND_ORDER_ID,
                "merchant-user", MERCHANT_ACCOUNT_ID, PAYER_ACCOUNT_ID, FundingSource.MINI_CREDIT,
                100L, "idem-key-00000001", "LOW", "0123456789abcdef0123456789abcdef",
                CREDIT_PAY_TX, Instant.parse("2026-08-04T08:00:00Z"));
    }

    private static FundTransaction balanceRefundTransaction() {
        return FundTransaction.acceptRefund(BALANCE_REFUND_TX, SourceType.REFUND_ORDER, REFUND_ORDER_ID,
                "merchant-user", MERCHANT_ACCOUNT_ID, PAYER_ACCOUNT_ID, FundingSource.BALANCE,
                100L, "idem-key-00000001", "LOW", "0123456789abcdef0123456789abcdef",
                "01K1ORGT02GH3JK4MN5PQRSTVW", Instant.parse("2026-08-04T08:00:00Z"));
    }

    private static final String CREDIT_PAY_TX = "01K1CRP002GH3JK4MN5PQRSTVW";
    private static final String REFUND_TX = "01K1RFX002GH3JK4MN5PQRSTVW";
    private static final String BALANCE_REFUND_TX = "01K1RFB002GH3JK4MN5PQRSTVW";
    private static final String REFUND_ORDER_ID = "01K1RFOD02GH3JK4MN5PQRSTVW";
    private static final String QR_ORDER_ID = "01K1QROD02GH3JK4MN5PQRSTVW";
    private static final String CREDIT_ACCOUNT_ID = "01K1CRD002GH3JK4MN5PQRSTVW";
    private static final String MERCHANT_ACCOUNT_ID = "01K1MCH002GH3JK4MN5PQRSTVW";
    private static final String PAYER_ACCOUNT_ID = "01K1PAY002GH3JK4MN5PQRSTVW";
    private static void expectPost(MockRestServiceServer server, String path) {
        server.expect(once(), requestTo("http://account" + path)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());
    }

    private static void expectPayerTryWithStableKey(
            MockRestServiceServer server,
            FundTransaction transaction,
            SecureMaterialService secure
    ) {
        server.expect(once(), requestTo("http://account/internal/v1/tcc/balance/payer/try"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.xid").value("tcc:" + transaction.getTransactionId()))
                .andExpect(jsonPath("$.freezeId").value(
                        secure.stableId(transaction.getTransactionId() + ":payer")))
                .andRespond(withSuccess());
    }

    private static void expectFacts(
            MockRestServiceServer server,
            FundTransaction transaction,
            String response
    ) {
        server.expect(once(), requestTo(
                        "http://account/internal/v1/transaction-facts/" + transaction.getTransactionId()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private static String successFacts() {
        return "{\"successConsistent\":true,\"cancelConsistent\":false,"
                + "\"accountsConfirmed\":true,\"ledgerConfirmed\":true,"
                + "\"freezeConfirmed\":true,\"ledgerPosted\":true,"
                + "\"accountsCancelled\":false,\"ledgerCancelled\":false,"
                + "\"noActiveFreeze\":false}";
    }

    private static String cancelledFacts() {
        return "{\"successConsistent\":false,\"cancelConsistent\":true,"
                + "\"accountsConfirmed\":false,\"ledgerConfirmed\":false,"
                + "\"freezeConfirmed\":false,\"ledgerPosted\":false,"
                + "\"accountsCancelled\":true,\"ledgerCancelled\":true,"
                + "\"noActiveFreeze\":true}";
    }
    private static FundTransaction transaction() {
        return FundTransaction.accept("01K1TX0002GH3JK4MN5PQRSTV", TransactionType.TRANSFER,
                SourceType.TRANSFER_DRAFT, "01K1DRAFT02GH3JK4MN5PQRSTV", "payer-user",
                "payer-account", "payee-account", FundingSource.BALANCE, 100L, "idem-key-00000001",
                "LOW", "0123456789abcdef0123456789abcdef", Instant.parse("2026-08-04T08:00:00Z"));
    }

    private static FundTransaction creditTransaction() {
        return FundTransaction.accept("01K1TXC002GH3JK4MN5PQRSTV", TransactionType.CREDIT_PAY,
                SourceType.QR_PAY_ORDER, "01K1QR0002GH3JK4MN5PQRSTV", "payer-user",
                "payer-account", "payee-account", FundingSource.MINI_CREDIT, 100L,
                "idem-key-credit-0001", "LOW", "0123456789abcdef0123456789abcdef",
                Instant.parse("2026-08-04T08:00:00Z"));
    }
}
