package com.minialalipay.business.application.qrpay;

import com.minialalipay.business.application.manualcase.ManualCaseApplicationService;
import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.ManualCaseStore;
import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.application.port.RiskDecisionStore;
import com.minialalipay.business.application.port.RiskHistoryPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.application.risk.RiskEvaluationService;
import com.minialalipay.business.application.risk.RiskReviewRouter;
import com.minialalipay.business.domain.confirmation.Confirmation;
import com.minialalipay.business.domain.manualcase.ManualCase;
import com.minialalipay.business.domain.manualcase.ManualCaseStatus;
import com.minialalipay.business.domain.manualcase.ManualCaseType;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderEvent;
import com.minialalipay.business.domain.risk.RiskDecision;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 动态二维码非资金应用服务测试，覆盖令牌、会话、过期和 CAS 边界。 */
class QrPayApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void 创建重试不返回原始令牌且不创建第二个订单() {
        MemoryStore store = new MemoryStore();
        QrPayApplicationService service = service(store, NOW);

        QrPayApplicationService.CreatedOrder first = service.create("payee-1", 100, "午餐", KEY);
        QrPayApplicationService.CreatedOrder replay = service.create("payee-1", 100, "午餐", KEY);

        assertEquals(first.order().getOrderId(), replay.order().getOrderId());
        assertNull(replay.rawToken());
        assertEquals(1, store.orders.size());
    }

    @Test
    void 令牌仅允许同一会话重复交换且扫码使用CAS推进() {
        MemoryStore store = new MemoryStore();
        QrPayApplicationService service = service(store, NOW);
        QrPayApplicationService.CreatedOrder created = service.create("payee-1", 100, "午餐", KEY);

        QrPayOrder exchanged = service.exchange("http-session-a", created.rawToken());
        QrPayOrder replayed = service.exchange("http-session-a", created.rawToken());
        assertEquals(1L, exchanged.getVersion());
        assertEquals(1L, replayed.getVersion());

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.exchange("http-session-b", created.rawToken()));
        assertEquals(BusinessErrorCode.QR_TOKEN_CONSUMED, conflict.errorCode());

        QrPayOrder scanned = service.scan(exchanged.getOrderId(), "http-session-a");
        assertEquals("SCANNED", scanned.getStatus().name());
        assertEquals(2L, scanned.getVersion());
    }

    @Test
    void 过期订单不得交换且CAS失败不得伪造成功() {
        MemoryStore store = new MemoryStore();
        QrPayApplicationService creator = service(store, NOW);
        QrPayApplicationService.CreatedOrder created = creator.create("payee-1", 100, "午餐", KEY);

        BusinessException expired = assertThrows(BusinessException.class,
                () -> service(store, NOW.plusSeconds(300)).exchange("session-a", created.rawToken()));
        assertEquals(BusinessErrorCode.ORDER_EXPIRED, expired.errorCode());
        assertEquals("EXPIRED", store.findById(created.order().getOrderId()).orElseThrow().getStatus().name());

        QrPayApplicationService.CreatedOrder second = creator.create("payee-1", 100, "晚餐",
                "123e4567-e89b-12d3-a456-426614174001");
        store.rejectUpdate = true;
        BusinessException versionConflict = assertThrows(BusinessException.class,
                () -> creator.exchange("session-a", second.rawToken()));
        assertEquals(BusinessErrorCode.VERSION_CONFLICT, versionConflict.errorCode());
    }

    @Test
    void 余额付款消费确认并创建处理中统一交易而非直接成功() {
        MemoryStore store = new MemoryStore();
        TestSecurity security = new TestSecurity();
        BusinessStore businessStore = mock(BusinessStore.class);
        PaymentProofPort proofs = mock(PaymentProofPort.class);
        TccCoordinatorPort coordinator = mock(TccCoordinatorPort.class);
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-" + userId, userId, "ACTIVE");
        QrPayApplicationService service = new QrPayApplicationService(store, accounts, security, new IdempotencyKeyValidator(),
                businessStore, proofs, coordinator, null, null, Clock.fixed(NOW, ZoneOffset.UTC));
        QrPayApplicationService.CreatedOrder created = service.create("payee-1", 100, "午餐", KEY);
        service.exchange("session-a", created.rawToken());
        service.scan(created.order().getOrderId(), "session-a");
        when(proofs.verify("payer-1", "proof-1", "QR_PAY_CONFIRM"))
                .thenReturn(new PaymentProofPort.VerifiedProof("proof-id", 3));

        QrPayApplicationService.IssuedConfirmation issued = service.issueConfirmation("payer-1", created.order().getOrderId(),
                "session-a", 2L, "proof-1", FundingSource.BALANCE);
        verify(businessStore).replaceQrPayConfirmation(any(Confirmation.class), org.mockito.ArgumentMatchers.eq(2L), any(QrPayOrder.class));
        when(businessStore.findConfirmationForUpdate(any())).thenAnswer(invocation -> {
            byte[] digest = invocation.getArgument(0);
            return java.util.Optional.of(new Confirmation("confirmation-1", digest,
                    com.minialalipay.business.domain.confirmation.SubjectType.QR_PAY_ORDER, created.order().getOrderId(),
                    security.digest(created.order().getOrderId() + "\n3\n" + "payee-1\naccount-payee-1\npayer-1\naccount-payer-1\n100\nBALANCE\n3"),
                    "payer-1", "proof-id", 3,
                    com.minialalipay.business.domain.confirmation.ConfirmationStatus.ACTIVE, NOW.plusSeconds(120), null, NOW));
        });
        when(businessStore.updateConfirmation(any(), anyString())).thenReturn(true);
        when(proofs.currentPayPasswordVersion("payer-1")).thenReturn(3L);

        var transaction = service.pay("payer-1", created.order().getOrderId(), "session-a", issued.confirmationToken(),
                "123e4567-e89b-12d3-a456-426614174009", "0".repeat(32));

        assertEquals("PROCESSING", transaction.getStatus().name());
        assertEquals("QR_PAY", transaction.getBusinessType().name());
        verify(businessStore).createTransaction(any(), any(), anyString(), any());
        verify(coordinator).startOrResume(any());
    }

    @Test
    void 命中转人工规则时订单进入人工复核且不签发确认令牌() {
        MemoryStore store = new MemoryStore();
        TestSecurity security = new TestSecurity();
        BusinessStore businessStore = mock(BusinessStore.class);
        PaymentProofPort proofs = mock(PaymentProofPort.class);
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-" + userId, userId, "ACTIVE");
        // 高频历史触发 R-03 转人工规则；决策保存到内存仓。
        MemoryRiskStore riskStore = new MemoryRiskStore();
        RiskHistoryPort history = new RiskHistoryPort() {
            @Override public int countRecentPayments(String payerUserId, Instant since) { return 6; }
            @Override public int countRepeatedPayments(String payerUserId, String payeeAccountId, long amountFen, Instant since) { return 0; }
            @Override public boolean hasTradedWith(String payerUserId, String payeeAccountId) { return false; }
        };
        MemoryManualStore manualStore = new MemoryManualStore();
        RiskReviewRouter router = new RiskReviewRouter(store, null,
                new ManualCaseApplicationService(manualStore, security));
        QrPayApplicationService service = new QrPayApplicationService(store, accounts, security,
                new IdempotencyKeyValidator(), businessStore, proofs, mock(TccCoordinatorPort.class),
                new RiskEvaluationService(riskStore, history, security), router, Clock.fixed(NOW, ZoneOffset.UTC));
        QrPayApplicationService.CreatedOrder created = service.create("payee-1", 100, "午餐", KEY);
        service.exchange("session-a", created.rawToken());
        service.scan(created.order().getOrderId(), "session-a");
        when(proofs.verify("payer-1", "proof-1", "QR_PAY_CONFIRM"))
                .thenReturn(new PaymentProofPort.VerifiedProof("proof-id", 3));

        BusinessException review = assertThrows(BusinessException.class,
                () -> service.issueConfirmation("payer-1", created.order().getOrderId(), "session-a", 2L,
                        "proof-1", FundingSource.BALANCE));

        assertEquals(BusinessErrorCode.RISK_MANUAL_REVIEW, review.errorCode());
        assertEquals("RISK_REVIEW", store.findById(created.order().getOrderId()).orElseThrow().getStatus().name());
        assertEquals(1, manualStore.created.size());
        verify(businessStore, never()).replaceQrPayConfirmation(any(Confirmation.class), anyLong(), any(QrPayOrder.class));
    }

    @Test
    void 处理中二维码订单读取必须回源统一交易主单() {
        MemoryStore store = new MemoryStore();
        TestSecurity security = new TestSecurity();
        BusinessStore businessStore = mock(BusinessStore.class);
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-" + userId, userId, "ACTIVE");
        QrPayApplicationService service = new QrPayApplicationService(store, accounts, security, new IdempotencyKeyValidator(),
                businessStore, mock(PaymentProofPort.class), mock(TccCoordinatorPort.class), null, null, Clock.fixed(NOW, ZoneOffset.UTC));
        QrPayApplicationService.CreatedOrder created = service.create("payee-1", 100, "午餐", KEY);
        service.exchange("session-a", created.rawToken());
        service.scan(created.order().getOrderId(), "session-a");
        QrPayOrder order = store.findById(created.order().getOrderId()).orElseThrow();
        order.lockPayer("payer-1", "account-payer-1", 2L, NOW);
        order.acceptByFundTransaction(3L, "transaction-1", NOW);
        FundTransaction transaction = FundTransaction.accept("transaction-1", TransactionType.QR_PAY, SourceType.QR_PAY_ORDER,
                order.getOrderId(), "payer-1", "account-payer-1", "account-payee-1", FundingSource.BALANCE, 100,
                KEY, "LOW", "0".repeat(32), NOW);
        transaction.publishSuccess(true, NOW.plusSeconds(1));
        when(businessStore.findTransaction("transaction-1"))
                .thenReturn(Optional.of(new BusinessStore.FundTransactionRecord(transaction, null)));

        QrPayOrder result = service.getForAuthorizedUser("payer-1", "session-a", order.getOrderId());

        assertEquals("SUCCESS", result.getStatus().name());
        verify(businessStore).findTransaction("transaction-1");
    }

    private static QrPayApplicationService service(MemoryStore store, Instant now) {
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-" + userId, userId, "ACTIVE");
        return new QrPayApplicationService(store, accounts, new TestSecurity(), new IdempotencyKeyValidator(),
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static final class MemoryStore implements QrPayStore {
        private final Map<String, QrPayOrder> orders = new HashMap<>();
        private final Map<String, String> tokenOrders = new HashMap<>();
        private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();
        private boolean rejectUpdate;

        @Override public Optional<QrPayOrder> findById(String orderId) { return Optional.ofNullable(orders.get(orderId)); }
        @Override public Optional<QrPayOrder> findByTokenDigest(byte[] tokenDigest) {
            return Optional.ofNullable(orders.get(tokenOrders.get(java.util.HexFormat.of().formatHex(tokenDigest))));
        }
        @Override public List<QrPayOrder> findByPayeeUserId(String userId, String status, int limit) {
            return orders.values().stream().filter(o -> o.getPayeeUserId().equals(userId))
                    .filter(o -> status == null || status.isBlank() || o.getStatus().name().equals(status)).limit(limit).toList();
        }
        @Override public boolean create(QrPayOrder order, byte[] tokenDigest, String recordId, String userId,
                                        String key, byte[] requestDigest) {
            if (idempotency.putIfAbsent(userId + ":CREATE_QR_PAY:" + key, new IdempotencyRecord(requestDigest, order.getOrderId())) != null) return false;
            orders.put(order.getOrderId(), order);
            tokenOrders.put(java.util.HexFormat.of().formatHex(tokenDigest), order.getOrderId());
            return true;
        }
        @Override public boolean update(QrPayOrder order, long expectedVersion) { return !rejectUpdate; }
        @Override public void appendOrderEvent(QrPayOrderEvent event) { }
        @Override public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String key) {
            return Optional.ofNullable(idempotency.get(principal + ':' + operation + ':' + key));
        }
        @Override public boolean reserveIdempotency(String recordId, String principal, String operation, String key,
                                                    byte[] requestDigest, String orderId) {
            return idempotency.putIfAbsent(principal + ':' + operation + ':' + key,
                    new IdempotencyRecord(requestDigest, orderId)) == null;
        }
    }

    private static final class MemoryRiskStore implements RiskDecisionStore {
        @Override public Optional<RiskDecision> findLatestBySubject(String subjectType, String subjectId) { return Optional.empty(); }
        @Override public boolean save(RiskDecision decision) { return true; }
    }

    private static final class MemoryManualStore implements ManualCaseStore {
        private final List<ManualCase> created = new ArrayList<>();
        @Override public List<ManualCase> list(String cursor, ManualCaseStatus status, ManualCaseType type, int limit) {
            return created;
        }
        @Override public Optional<ManualCase> find(String caseId) {
            return created.stream().filter(c -> c.getCaseId().equals(caseId)).findFirst();
        }
        @Override public boolean create(ManualCase manualCase) { created.add(manualCase); return true; }
        @Override public boolean update(ManualCase manualCase, long expectedVersion) { return true; }
        @Override public Optional<DecisionIdempotencyRecord> findDecisionIdempotency(String operatorId, String key) { return Optional.empty(); }
        @Override public boolean reserveDecisionIdempotency(String recordId, String operatorId, String key, byte[] hash) { return true; }
        @Override public void completeDecisionIdempotency(String operatorId, String key, ManualCase manualCase) { }
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int sequence;
        @Override public String newId() { return "id-" + ++sequence; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "unused"; }
        @Override public String newQrToken() { return "qr-token-" + ++sequence + "-0123456789abcdef"; }
        @Override public String newCollectionToken() { return "unused-collection"; }
        @Override public byte[] digest(String value) {
            try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        @Override public String stableId(String value) { return java.util.HexFormat.of().formatHex(digest(value)).substring(0, 26); }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
