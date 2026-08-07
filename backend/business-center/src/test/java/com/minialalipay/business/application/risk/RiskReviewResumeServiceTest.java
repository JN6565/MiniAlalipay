package com.minialalipay.business.application.risk;

import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionOrderStatus;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderStatus;
import com.minialalipay.business.domain.qrpay.QrTokenDigest;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 人工复核批准后的订单恢复服务测试。 */
class RiskReviewResumeServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 动态扫码订单从人工复核恢复为待确认() {
        MemoryQrStore store = new MemoryQrStore();
        store.order = reviewQrOrder("order-1");
        store.dbVersion = store.order.getVersion();
        RiskReviewResumeService service = new RiskReviewResumeService(store, null);

        boolean resumed = service.resumeToConfirmation("QR_PAY_ORDER", "order-1", NOW.plusSeconds(5));

        assertTrue(resumed);
        assertEquals(QrPayOrderStatus.PENDING_CONFIRMATION, store.order.getStatus());
    }

    @Test
    void C2C订单从人工复核恢复为待确认() {
        MemoryCollectionStore store = new MemoryCollectionStore();
        CollectionOrder order = CollectionOrder.forPersonalCode("c2c-1", "code-1", "payee-1", "account-payee-1",
                "payer-1", "account-payer-1", NOW);
        order.lockPersonalAmount("payer-1", 0L, 5200L, "午餐", NOW.plusSeconds(1));
        order.markRiskReview(1L, NOW.plusSeconds(2));
        store.order = order;
        store.dbVersion = order.getVersion();
        RiskReviewResumeService service = new RiskReviewResumeService(null, store);

        boolean resumed = service.resumeToConfirmation("PERSONAL_QR_ORDER", "c2c-1", NOW.plusSeconds(5));

        assertTrue(resumed);
        assertEquals(CollectionOrderStatus.PENDING_CONFIRMATION, store.order.getStatus());
    }

    @Test
    void 不在复核状态或主体不存在视为已处理() {
        MemoryQrStore store = new MemoryQrStore();
        store.order = QrPayOrder.create("order-1", "payee-1", "account-payee-1", 5200L, "午餐",
                QrTokenDigest.fromHex("ab".repeat(32)), NOW);
        store.dbVersion = store.order.getVersion();
        RiskReviewResumeService service = new RiskReviewResumeService(store, null);

        assertTrue(service.resumeToConfirmation("QR_PAY_ORDER", "order-1", NOW.plusSeconds(5)));
        assertTrue(service.resumeToConfirmation("QR_PAY_ORDER", "order-missing", NOW.plusSeconds(5)));
    }

    private static QrPayOrder reviewQrOrder(String orderId) {
        QrPayOrder order = QrPayOrder.create(orderId, "payee-1", "account-payee-1", 5200L, "午餐",
                QrTokenDigest.fromHex("ab".repeat(32)), NOW);
        order.exchangeToken("session-1", QrTokenDigest.fromHex("ab".repeat(32)), NOW.plusSeconds(1));
        order.scan("session-1", 1L, NOW.plusSeconds(2));
        order.lockPayer("payer-1", "account-payer-1", 2L, NOW.plusSeconds(3));
        order.markRiskReview(3L, NOW.plusSeconds(4));
        return order;
    }

    private static final class MemoryQrStore implements QrPayStore {
        private QrPayOrder order;
        private long dbVersion;
        @Override public Optional<QrPayOrder> findById(String orderId) { return Optional.ofNullable(order); }
        @Override public Optional<QrPayOrder> findByTokenDigest(byte[] tokenDigest) { return Optional.empty(); }
        @Override public List<QrPayOrder> findByPayeeUserId(String userId, String status, int limit) { return List.of(); }
        @Override public boolean create(QrPayOrder order, byte[] digest, String recordId, String user, String key, byte[] request) { return true; }
        @Override public boolean update(QrPayOrder order, long expectedVersion) {
            if (this.order == null || dbVersion != expectedVersion) return false;
            this.order = order;
            this.dbVersion = order.getVersion();
            return true;
        }
        @Override public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String key) { return Optional.empty(); }
        @Override public boolean reserveIdempotency(String recordId, String principal, String operation, String key, byte[] digest, String orderId) { return true; }
    }

    private static final class MemoryCollectionStore implements CollectionStore {
        private CollectionOrder order;
        private long dbVersion;
        @Override public Optional<CollectionOrder> findOrder(String orderId) { return Optional.ofNullable(order); }
        @Override public boolean updateOrder(CollectionOrder order, long expectedVersion) {
            if (this.order == null || dbVersion != expectedVersion) return false;
            this.order = order;
            this.dbVersion = order.getVersion();
            return true;
        }
        @Override public void clearSessionBinding(String orderId) { }
        @Override public Optional<com.minialalipay.business.domain.collection.PersonalCollectionCode> findActiveCode(String userId) { return Optional.empty(); }
        @Override public Optional<com.minialalipay.business.domain.collection.PersonalCollectionCode> findCode(String codeId) { return Optional.empty(); }
        @Override public Optional<com.minialalipay.business.domain.collection.PersonalCollectionCode> findActiveCodeByTokenDigest(byte[] tokenDigest) { return Optional.empty(); }
        @Override public boolean replaceCode(com.minialalipay.business.domain.collection.PersonalCollectionCode oldCode, com.minialalipay.business.domain.collection.PersonalCollectionCode newCode, byte[] tokenDigest, String recordId, String userId, String idempotencyKey, byte[] requestDigest) { return true; }
        @Override public boolean updateCode(com.minialalipay.business.domain.collection.PersonalCollectionCode code, long expectedVersion) { return true; }
        @Override public Optional<com.minialalipay.business.domain.collection.CollectionRequest> findRequest(String requestId) { return Optional.empty(); }
        @Override public Optional<com.minialalipay.business.domain.collection.CollectionRequest> findRequestByTokenDigest(byte[] tokenDigest) { return Optional.empty(); }
        @Override public Optional<CollectionOrder> findOrderByBootstrapSessionId(String bootstrapSessionId) { return Optional.empty(); }
        @Override public boolean createPersonalOrder(CollectionOrder order, String bootstrapSessionId) { return true; }
                @Override public boolean createFixedOrder(CollectionOrder order, String bootstrapSessionId) { return true; }
                @Override public java.util.List<CollectionOrder> findOrdersByRequestId(String requestId) { return java.util.List.of(); }
        @Override public boolean createRequest(com.minialalipay.business.domain.collection.CollectionRequest request, byte[] tokenDigest, String recordId, String userId, String idempotencyKey, byte[] requestDigest) { return true; }
        @Override public boolean updateRequest(com.minialalipay.business.domain.collection.CollectionRequest request, long expectedVersion) { return true; }
        @Override public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String idempotencyKey) { return Optional.empty(); }
        @Override public boolean reserveIdempotency(String recordId, String principal, String operation, String idempotencyKey, byte[] requestDigest, String resourceId, String resourceType) { return true; }
    }
}
