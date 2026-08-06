package com.minialalipay.business.application.refund;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.RefundStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.domain.refund.RefundOrder;
import com.minialalipay.business.domain.refund.RefundOrderStatus;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 退款来源订单应用服务测试：原交易校验、幂等、受理与对象权限。 */
class RefundApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";
    private static final String KEY2 = "123e4567-e89b-12d3-a456-426614174001";
    private static final String REASON = "商品质量问题";

    private static FundTransaction successQrPay(String payeeAccountId) {
        FundTransaction original = FundTransaction.accept("t-original", TransactionType.QR_PAY, SourceType.QR_PAY_ORDER,
                "qr-order-1", "payer-1", "account-payer-1", payeeAccountId, FundingSource.BALANCE, 100,
                "orig-key", "LOW", "0".repeat(32), NOW);
        original.publishSuccess(true, NOW.plusSeconds(1));
        return original;
    }

    private static RefundApplicationService service(MemoryRefundStore store, FundTransaction original) {
        BusinessStore businessStore = mock(BusinessStore.class);
        when(businessStore.findTransaction(any())).thenAnswer(invocation ->
                original.getTransactionId().equals(invocation.getArgument(0))
                        ? Optional.of(new BusinessStore.FundTransactionRecord(original, null)) : Optional.empty());
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-payee-1", userId, "ACTIVE");
        TestSecurity security = new TestSecurity();
        return new RefundApplicationService(store, businessStore, accounts, security, new IdempotencyKeyValidator(),
                mock(TccCoordinatorPort.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 对本人已成功动态扫码交易创建退款订单() {
        MemoryRefundStore store = new MemoryRefundStore();
        RefundApplicationService service = service(store, successQrPay("account-payee-1"));

        RefundOrder order = service.create("payee-1", "t-original", REASON, KEY);

        assertEquals(RefundOrderStatus.CREATED, order.getStatus());
        assertEquals("t-original", order.getOriginalTransactionId());
        assertEquals("account-payee-1", order.getMerchantAccountId());
        assertEquals("account-payer-1", order.getPayerAccountId());
        assertEquals("QR_PAY", order.getOriginalBusinessType());
        assertEquals("BALANCE", order.getFundingSource());
        assertEquals(REASON, order.getReasonCode());
        assertEquals(100, order.getAmountFen());
    }

    @Test
    void 原交易非成功扫码或非本人收款时拒绝() {
        // 非扫码类型（主动转账）。
        FundTransaction transfer = FundTransaction.accept("t-transfer", TransactionType.TRANSFER, SourceType.TRANSFER_DRAFT,
                "draft-1", "payer-1", "account-payer-1", "account-payee-1", FundingSource.BALANCE, 100,
                "orig-key", "LOW", "0".repeat(32), NOW);
        transfer.publishSuccess(true, NOW.plusSeconds(1));
        BusinessException typeError = assertThrows(BusinessException.class,
                () -> service(new MemoryRefundStore(), transfer).create("payee-1", "t-transfer", REASON, KEY));
        assertEquals(BusinessErrorCode.REFUND_NOT_ALLOWED, typeError.errorCode());

        // 非成功状态。
        FundTransaction processing = FundTransaction.accept("t-proc", TransactionType.QR_PAY, SourceType.QR_PAY_ORDER,
                "qr-order-1", "payer-1", "account-payer-1", "account-payee-1", FundingSource.BALANCE, 100,
                "orig-key", "LOW", "0".repeat(32), NOW);
        BusinessException statusError = assertThrows(BusinessException.class,
                () -> service(new MemoryRefundStore(), processing).create("payee-1", "t-proc", REASON, KEY2));
        assertEquals(BusinessErrorCode.REFUND_NOT_ALLOWED, statusError.errorCode());

        // 非本人收款（本人账户与收款账户不一致）。
        BusinessException ownerError = assertThrows(BusinessException.class,
                () -> service(new MemoryRefundStore(), successQrPay("account-other")).create("payee-1", "t-original", REASON, KEY2));
        assertEquals(BusinessErrorCode.REFUND_NOT_ALLOWED, ownerError.errorCode());
    }

    @Test
    void 同一原交易不能重复发起退款() {
        MemoryRefundStore store = new MemoryRefundStore();
        RefundApplicationService service = service(store, successQrPay("account-payee-1"));
        service.create("payee-1", "t-original", REASON, KEY);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.create("payee-1", "t-original", REASON, KEY2));

        assertEquals(BusinessErrorCode.REFUND_ALREADY_EXISTS, error.errorCode());
    }

    @Test
    void 创建幂等键复用返回既有订单() {
        MemoryRefundStore store = new MemoryRefundStore();
        RefundApplicationService service = service(store, successQrPay("account-payee-1"));

        RefundOrder first = service.create("payee-1", "t-original", REASON, KEY);
        RefundOrder replay = service.create("payee-1", "t-original", REASON, KEY);

        assertEquals(first.getRefundOrderId(), replay.getRefundOrderId());
        assertEquals(1, store.orders.size());
    }

    @Test
    void 提交执行受理唯一REFUND交易并启动TCC() {
        MemoryRefundStore store = new MemoryRefundStore();
        BusinessStore businessStore = mock(BusinessStore.class);
        FundTransaction original = successQrPay("account-payee-1");
        when(businessStore.findTransaction(any())).thenAnswer(invocation ->
                "t-original".equals(invocation.getArgument(0))
                        ? Optional.of(new BusinessStore.FundTransactionRecord(original, null)) : Optional.empty());
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-payee-1", userId, "ACTIVE");
        TccCoordinatorPort coordinator = mock(TccCoordinatorPort.class);
        RefundApplicationService service = new RefundApplicationService(store, businessStore, accounts,
                new TestSecurity(), new IdempotencyKeyValidator(), coordinator, Clock.fixed(NOW, ZoneOffset.UTC));
        RefundOrder created = service.create("payee-1", "t-original", REASON, KEY);

        RefundOrder submitted = service.submit("payee-1", created.getRefundOrderId(), 0L, KEY2, "0".repeat(32));

        assertEquals(RefundOrderStatus.PROCESSING, submitted.getStatus());
        org.mockito.ArgumentCaptor<FundTransaction> captor = forClass(FundTransaction.class);
        verify(businessStore).createTransaction(captor.capture(), any(), any(), any());
        FundTransaction refundTransaction = captor.getValue();
        assertEquals(TransactionType.REFUND, refundTransaction.getBusinessType());
        assertEquals(SourceType.REFUND_ORDER, refundTransaction.getSourceType());
        assertEquals(created.getRefundOrderId(), refundTransaction.getSourceOrderId());
        assertEquals("account-payee-1", refundTransaction.getPayerAccountId());
        assertEquals("account-payer-1", refundTransaction.getPayeeAccountId());
        assertEquals(FundingSource.BALANCE, refundTransaction.getFundingSource());
        verify(coordinator).startOrResume(any());
    }

    @Test
    void 查询与提交强制本人对象权限() {
        MemoryRefundStore store = new MemoryRefundStore();
        RefundApplicationService service = service(store, successQrPay("account-payee-1"));
        RefundOrder created = service.create("payee-1", "t-original", REASON, KEY);

        assertThrows(BusinessException.class, () -> service.get("other-user", created.getRefundOrderId()));
        assertThrows(BusinessException.class,
                () -> service.submit("other-user", created.getRefundOrderId(), 0L, KEY2, "0".repeat(32)));
    }

    /** 返回订单不可变副本，避免内存存储共享可变对象导致 CAS 断言失真。 */
    private static RefundOrder copy(RefundOrder order) {
        return new RefundOrder(order.getRefundOrderId(), order.getOriginalTransactionId(),
                order.getMerchantUserId(), order.getMerchantAccountId(), order.getPayerUserId(), order.getPayerAccountId(),
                order.getOriginalBusinessType(), order.getFundingSource(), order.getAmountFen(), order.getReasonCode(),
                order.getStatus(), order.getTransactionId(), order.getVersion(),
                order.getCreatedAt(), order.getUpdatedAt(), order.getCompletedAt());
    }

    private static final class MemoryRefundStore implements RefundStore {
        private final Map<String, RefundOrder> orders = new HashMap<>();
        private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();
        @Override public Optional<RefundOrder> findById(String refundOrderId) {
            return Optional.ofNullable(orders.get(refundOrderId)).map(RefundApplicationServiceTest::copy);
        }
        @Override public Optional<RefundOrder> findByOriginalTransactionId(String originalTransactionId) {
            return orders.values().stream().filter(o -> o.getOriginalTransactionId().equals(originalTransactionId)).findFirst();
        }
        @Override public List<RefundOrder> findByMerchantUserId(String merchantUserId, String status, int limit) {
            return orders.values().stream().filter(o -> o.getMerchantUserId().equals(merchantUserId))
                    .filter(o -> status == null || status.isBlank() || o.getStatus().name().equals(status)).limit(limit).toList();
        }
        @Override public boolean create(RefundOrder order) {
            orders.put(order.getRefundOrderId(), order);
            return true;
        }
        @Override public boolean update(RefundOrder order, long expectedVersion) {
            RefundOrder current = orders.get(order.getRefundOrderId());
            if (current == null || current.getVersion() != expectedVersion) return false;
            orders.put(order.getRefundOrderId(), order);
            return true;
        }
        @Override public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String idempotencyKey) {
            return Optional.ofNullable(idempotency.get(principal + ":" + operation + ":" + idempotencyKey));
        }
        @Override public boolean reserveIdempotency(String recordId, String principal, String operation, String idempotencyKey,
                                                    byte[] requestDigest, String resourceId) {
            return idempotency.putIfAbsent(principal + ":" + operation + ":" + idempotencyKey,
                    new IdempotencyRecord(requestDigest, resourceId)) == null;
        }
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int sequence;
        @Override public String newId() { return "id-" + ++sequence; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "unused"; }
        @Override public String newQrToken() { return "unused-qr"; }
        @Override public String newCollectionToken() { return "unused-collection"; }
        @Override public byte[] digest(String value) {
            try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        @Override public String stableId(String value) { return java.util.HexFormat.of().formatHex(digest(value)).substring(0, 26); }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
