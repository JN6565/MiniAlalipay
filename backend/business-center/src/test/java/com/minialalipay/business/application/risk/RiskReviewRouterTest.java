package com.minialalipay.business.application.risk;

import com.minialalipay.business.application.manualcase.ManualCaseApplicationService;
import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.ManualCaseStore;
import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionOrderStatus;
import com.minialalipay.business.domain.manualcase.ManualCase;
import com.minialalipay.business.domain.manualcase.ManualCaseStatus;
import com.minialalipay.business.domain.manualcase.ManualCaseType;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderStatus;
import com.minialalipay.business.domain.qrpay.QrTokenDigest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 风控复核路由测试：订单转 RISK_REVIEW、创建预检工单，并对已复核订单幂等。 */
class RiskReviewRouterTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 动态扫码订单路由到人工复核并创建预检工单() {
        MemoryQrStore qrStore = new MemoryQrStore();
        MemoryManualCaseStore caseStore = new MemoryManualCaseStore();
        QrPayOrder order = pendingQrOrder("order-1", qrStore);
        RiskReviewRouter router = router(qrStore, null, caseStore);

        router.routeQrPayOrderToReview(order, 2L, "RISK_MANUAL_REVIEW", NOW);

        assertEquals(QrPayOrderStatus.RISK_REVIEW, order.getStatus());
        assertNotNull(qrStore.lastUpdated);
        assertNotNull(caseStore.findBySubject("QR_PAY_ORDER", "order-1"));
        ManualCase created = caseStore.created.get(0);
        assertEquals(ManualCaseType.RISK_PRECHECK, created.getType());
    }

    @Test
    void 已复核订单重复路由保持幂等且不重复建单() {
        MemoryQrStore qrStore = new MemoryQrStore();
        MemoryManualCaseStore caseStore = new MemoryManualCaseStore();
        QrPayOrder order = pendingQrOrder("order-1", qrStore);
        RiskReviewRouter router = router(qrStore, null, caseStore);
        router.routeQrPayOrderToReview(order, 2L, "RISK_MANUAL_REVIEW", NOW);
        assertEquals(1, caseStore.created.size());

        router.routeQrPayOrderToReview(order, 2L, "RISK_MANUAL_REVIEW", NOW);

        assertEquals(QrPayOrderStatus.RISK_REVIEW, order.getStatus());
        assertEquals(1, caseStore.created.size());
    }

    @Test
    void C2C订单路由到人工复核并创建预检工单() {
        MemoryCollectionStore collectionStore = new MemoryCollectionStore();
        MemoryManualCaseStore caseStore = new MemoryManualCaseStore();
        CollectionOrder order = CollectionOrder.forPersonalCode("c2c-1", "code-1", "payee-1", "account-payee-1",
                "payer-1", "account-payer-1", NOW);
        order.lockPersonalAmount("payer-1", 0L, 5200L, "午餐", NOW);
        RiskReviewRouter router = router(null, collectionStore, caseStore);

        router.routeCollectionOrderToReview(order, 1L, "PERSONAL_QR_ORDER", "RISK_MANUAL_REVIEW", NOW);

        assertEquals(CollectionOrderStatus.RISK_REVIEW, order.getStatus());
        assertNotNull(collectionStore.lastUpdated);
        assertNotNull(caseStore.findBySubject("PERSONAL_QR_ORDER", "c2c-1"));
    }

    private static RiskReviewRouter router(MemoryQrStore qrStore, MemoryCollectionStore collectionStore,
                                           MemoryManualCaseStore caseStore) {
        return new RiskReviewRouter(qrStore, collectionStore,
                new ManualCaseApplicationService(caseStore, new TestSecurity()));
    }

    private static QrPayOrder pendingQrOrder(String orderId, MemoryQrStore store) {
        QrPayOrder order = QrPayOrder.create(orderId, "payee-1", "account-payee-1", 5200L, "午餐",
                QrTokenDigest.fromHex("ab".repeat(32)), NOW);
        order.exchangeToken("session-1", QrTokenDigest.fromHex("ab".repeat(32)), NOW.plusSeconds(1));
        order.scan("session-1", 1L, NOW.plusSeconds(2));
        order.lockPayer("payer-1", "account-payer-1", 2L, NOW.plusSeconds(3));
        store.lastUpdated = order;
        return order;
    }

    private static final class MemoryQrStore implements QrPayStore {
        private QrPayOrder lastUpdated;
        @Override public Optional<QrPayOrder> findById(String orderId) { return Optional.ofNullable(lastUpdated); }
        @Override public Optional<QrPayOrder> findByTokenDigest(byte[] tokenDigest) { return Optional.empty(); }
        @Override public List<QrPayOrder> findByPayeeUserId(String userId, String status, int limit) { return List.of(); }
        @Override public boolean create(QrPayOrder order, byte[] digest, String recordId, String user, String key, byte[] request) { return true; }
        @Override public boolean update(QrPayOrder order, long expectedVersion) { lastUpdated = order; return true; }
        @Override public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String key) { return Optional.empty(); }
        @Override public boolean reserveIdempotency(String recordId, String principal, String operation, String key, byte[] digest, String orderId) { return true; }
    }

    private static final class MemoryCollectionStore implements CollectionStore {
        private CollectionOrder lastUpdated;
        @Override public Optional<com.minialalipay.business.domain.collection.PersonalCollectionCode> findActiveCode(String userId) { return Optional.empty(); }
        @Override public Optional<com.minialalipay.business.domain.collection.PersonalCollectionCode> findCode(String codeId) { return Optional.empty(); }
        @Override public Optional<com.minialalipay.business.domain.collection.PersonalCollectionCode> findActiveCodeByTokenDigest(byte[] tokenDigest) { return Optional.empty(); }
        @Override public boolean replaceCode(com.minialalipay.business.domain.collection.PersonalCollectionCode oldCode, com.minialalipay.business.domain.collection.PersonalCollectionCode newCode, byte[] tokenDigest, String recordId, String userId, String idempotencyKey, byte[] requestDigest) { return true; }
        @Override public boolean updateCode(com.minialalipay.business.domain.collection.PersonalCollectionCode code, long expectedVersion) { return true; }
        @Override public Optional<com.minialalipay.business.domain.collection.CollectionRequest> findRequest(String requestId) { return Optional.empty(); }
        @Override public Optional<com.minialalipay.business.domain.collection.CollectionRequest> findRequestByTokenDigest(byte[] tokenDigest) { return Optional.empty(); }
        @Override public Optional<CollectionOrder> findOrderByBootstrapSessionId(String bootstrapSessionId) { return Optional.empty(); }
        @Override public Optional<CollectionOrder> findOrder(String orderId) { return Optional.ofNullable(lastUpdated); }
        @Override public boolean createPersonalOrder(CollectionOrder order, String bootstrapSessionId) { return true; }
                @Override public boolean createFixedOrder(CollectionOrder order, String bootstrapSessionId) { return true; }
                @Override public java.util.List<CollectionOrder> findOrdersByRequestId(String requestId) { return java.util.List.of(); }
        @Override public boolean updateOrder(CollectionOrder order, long expectedVersion) { lastUpdated = order; return true; }
        // 内存 Mock 不维护 H5 会话绑定，清除为空操作。
        @Override public void clearSessionBinding(String orderId) { }
        @Override public boolean createRequest(com.minialalipay.business.domain.collection.CollectionRequest request, byte[] tokenDigest, String recordId, String userId, String idempotencyKey, byte[] requestDigest) { return true; }
        @Override public boolean updateRequest(com.minialalipay.business.domain.collection.CollectionRequest request, long expectedVersion) { return true; }
        @Override public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String idempotencyKey) { return Optional.empty(); }
        @Override public boolean reserveIdempotency(String recordId, String principal, String operation, String idempotencyKey, byte[] requestDigest, String resourceId, String resourceType) { return true; }
    }

    private static final class MemoryManualCaseStore implements ManualCaseStore {
        private final List<ManualCase> created = new ArrayList<>();
        private final Set<String> subjects = new HashSet<>();
        @Override public List<ManualCase> list(String cursor, ManualCaseStatus status, ManualCaseType type, int limit) {
            return created;
        }
        @Override public Optional<ManualCase> find(String caseId) { return created.stream().filter(c -> c.getCaseId().equals(caseId)).findFirst(); }
        @Override public boolean create(ManualCase manualCase) {
            String subject = manualCase.getSubjectType() + ":" + manualCase.getSubjectId();
            if (!subjects.add(subject)) return false;
            created.add(manualCase);
            return true;
        }
        @Override public boolean update(ManualCase manualCase, long expectedVersion) { return true; }
        @Override public Optional<DecisionIdempotencyRecord> findDecisionIdempotency(String operatorId, String key) { return Optional.empty(); }
        @Override public boolean reserveDecisionIdempotency(String recordId, String operatorId, String key, byte[] hash) { return true; }
        @Override public void completeDecisionIdempotency(String operatorId, String key, ManualCase manualCase) { }
        ManualCase findBySubject(String subjectType, String subjectId) {
            return created.stream().filter(c -> subjectType.equals(c.getSubjectType()) && subjectId.equals(c.getSubjectId()))
                    .findFirst().orElse(null);
        }
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int id;
        @Override public String newId() { return "id-" + ++id; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "unused"; }
        @Override public String newQrToken() { return "unused"; }
        @Override public String newCollectionToken() { return "unused"; }
        @Override public byte[] digest(String value) {
            try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        @Override public String stableId(String value) { return value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
