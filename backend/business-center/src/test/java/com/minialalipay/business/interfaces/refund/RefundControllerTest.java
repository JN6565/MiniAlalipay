package com.minialalipay.business.interfaces.refund;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.RefundStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.application.refund.RefundApplicationService;
import com.minialalipay.business.domain.refund.RefundOrder;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.business.interfaces.error.BusinessCenterExceptionHandler;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 退款来源订单 Controller 切片测试：创建、查询、提交与契约错误码。 */
class RefundControllerTest {
    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        RequestIdGenerator requestIds = new RequestIdGenerator();
        FundTransaction original = FundTransaction.accept("t-original", TransactionType.QR_PAY, SourceType.QR_PAY_ORDER,
                "qr-order-1", "payer-1", "account-payer-1", "account-payee-1", FundingSource.BALANCE, 100,
                "orig-key", "LOW", "0".repeat(32), NOW);
        original.publishSuccess(true, NOW.plusSeconds(1));
        BusinessStore businessStore = mock(BusinessStore.class);
        when(businessStore.findTransaction(any())).thenAnswer(invocation ->
                "t-original".equals(invocation.getArgument(0))
                        ? Optional.of(new BusinessStore.FundTransactionRecord(original, null)) : Optional.empty());
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-payee-1", userId, "ACTIVE");
        MemoryRefundStore store = new MemoryRefundStore();
        RefundApplicationService service = new RefundApplicationService(store, businessStore, accounts,
                new TestSecurity(), new IdempotencyKeyValidator(), mock(TccCoordinatorPort.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new RefundController(service, requestIds))
                .setControllerAdvice(new BusinessCenterExceptionHandler(new CommonExceptionMapper(), requestIds)).build();
    }

    @Test
    void 创建本人退款订单返回待提交状态() throws Exception {
        mvc.perform(post("/api/v1/refunds")
                        .header("X-User-Id", "payee-1").header("X-Request-Id", "req-refund-create")
                        .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalTransactionId\":\"t-original\",\"reasonCode\":\"商品质量问题\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value("req-refund-create"))
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.originalTransactionId").value("t-original"))
                .andExpect(jsonPath("$.data.amountFen").value(100));
    }

    @Test
    void 不支持的交易返回契约错误码() throws Exception {
        mvc.perform(post("/api/v1/refunds")
                        .header("X-User-Id", "payee-1").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalTransactionId\":\"t-unknown\",\"reasonCode\":\"原因\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void 查询与提交执行退款() throws Exception {
        mvc.perform(post("/api/v1/refunds")
                        .header("X-User-Id", "payee-1").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"originalTransactionId\":\"t-original\",\"reasonCode\":\"商品质量问题\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.refundOrderId").value("id-1"));

        mvc.perform(get("/api/v1/refunds/id-1").header("X-User-Id", "payee-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.refundOrderId").value("id-1"));

        mvc.perform(post("/api/v1/refunds/id-1/submit")
                        .header("X-User-Id", "payee-1")
                        .header("Idempotency-Key", "123e4567-e89b-12d3-a456-426614174001")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
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
            return Optional.ofNullable(orders.get(refundOrderId)).map(RefundControllerTest::copy);
        }
        @Override public Optional<RefundOrder> findByOriginalTransactionId(String originalTransactionId) {
            return orders.values().stream().filter(o -> o.getOriginalTransactionId().equals(originalTransactionId)).findFirst();
        }
        @Override public List<RefundOrder> findByMerchantUserId(String merchantUserId, String status, int limit) {
            return orders.values().stream().filter(o -> o.getMerchantUserId().equals(merchantUserId)).limit(limit).toList();
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
