package com.minialalipay.business.interfaces.qrpay;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.qrpay.QrPayApplicationService;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.interfaces.error.BusinessCenterExceptionHandler;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 动态二维码 Controller 切片测试，验证短期令牌不会进入订单响应字段。 */
class QrPayControllerTest {
    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";
    private MockMvc mvc;
    private MemoryStore store;

    @BeforeEach
    void setUp() {
        RequestIdGenerator requestIds = new RequestIdGenerator();
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-1", userId, "ACTIVE");
        store = new MemoryStore();
        QrPayApplicationService service = new QrPayApplicationService(store, accounts, new TestSecurity(),
                new IdempotencyKeyValidator());
        mvc = MockMvcBuilders.standaloneSetup(new QrPayController(service, requestIds))
                .setControllerAdvice(new BusinessCenterExceptionHandler(new CommonExceptionMapper(), requestIds)).build();
    }

    @Test
    void 创建查询和会话交换只返回脱敏订单() throws Exception {
        mvc.perform(post("/api/v1/qr-pay/orders")
                        .header("X-User-Id", "payee-1").header("X-Request-Id", "req-qr-create")
                        .header("Idempotency-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountFen\":100,\"subject\":\"午餐\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.requestId").value("req-qr-create"))
                .andExpect(jsonPath("$.data.qrOrderId").value("id-1"))
                .andExpect(jsonPath("$.data.qrCodeUrl").exists());

        mvc.perform(get("/api/v1/qr-pay/orders/id-1").header("X-User-Id", "payee-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.qrCodeUrl").doesNotExist())
                .andExpect(jsonPath("$.data.payeeDisplayName").doesNotExist());

        MockHttpSession session = new MockHttpSession(null, "session-1");
        mvc.perform(post("/api/v1/qr-pay/token-exchanges").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"qr-token-0123456789abcdef\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CREATED"));
        mvc.perform(post("/api/v1/qr-pay/orders/id-1/scan").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SCANNED"));
    }

    @Test
    void 确认接口拒绝缺少登录身份和严格请求体的调用() throws Exception {
        mvc.perform(post("/api/v1/qr-pay/orders/id-1/confirmations"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 订单详情仅对付款人收款人或绑定会话开放() throws Exception {
        mvc.perform(post("/api/v1/qr-pay/orders")
                        .header("X-User-Id", "payee-1").header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountFen\":100,\"subject\":\"午餐\"}"))
                .andExpect(status().isCreated());
        MockHttpSession boundSession = new MockHttpSession(null, "session-1");
        mvc.perform(post("/api/v1/qr-pay/token-exchanges").session(boundSession).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"qr-token-0123456789abcdef\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/qr-pay/orders/id-1/scan").session(boundSession)).andExpect(status().isOk());
        store.findById("id-1").orElseThrow().lockPayer("payer-1", "account-payer-1", 2L, Instant.now());

        mvc.perform(get("/api/v1/qr-pay/orders/id-1").header("X-User-Id", "payee-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/qr-pay/orders/id-1").header("X-User-Id", "payer-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/qr-pay/orders/id-1").session(boundSession).header("X-User-Id", "visitor-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/qr-pay/orders/id-1").header("X-User-Id", "visitor-2"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    private static final class MemoryStore implements QrPayStore {
        private final Map<String, QrPayOrder> orders = new HashMap<>();
        private final Map<String, String> tokenOrders = new HashMap<>();
        private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();
        @Override public Optional<QrPayOrder> findById(String id) { return Optional.ofNullable(orders.get(id)); }
        @Override public Optional<QrPayOrder> findByTokenDigest(byte[] digest) { return Optional.ofNullable(orders.get(tokenOrders.get(java.util.HexFormat.of().formatHex(digest)))); }
        @Override public List<QrPayOrder> findByPayeeUserId(String user, String status, int limit) { return orders.values().stream().toList(); }
        @Override public boolean create(QrPayOrder order, byte[] digest, String recordId, String user, String key, byte[] request) {
            if (idempotency.putIfAbsent(user + ":CREATE_QR_PAY:" + key, new IdempotencyRecord(request, order.getOrderId())) != null) return false;
            orders.put(order.getOrderId(), order); tokenOrders.put(java.util.HexFormat.of().formatHex(digest), order.getOrderId()); return true;
        }
        @Override public boolean update(QrPayOrder order, long version) { return true; }
        @Override public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String key) {
            return Optional.ofNullable(idempotency.get(principal + ':' + operation + ':' + key));
        }
        @Override public boolean reserveIdempotency(String recordId, String principal, String operation, String key, byte[] request, String orderId) {
            return idempotency.putIfAbsent(principal + ':' + operation + ':' + key, new IdempotencyRecord(request, orderId)) == null;
        }
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int id;
        @Override public String newId() { return "id-" + ++id; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "unused"; }
        @Override public String newQrToken() { return "qr-token-0123456789abcdef"; }
        @Override public String newCollectionToken() { return "unused-collection"; }
        @Override public byte[] digest(String value) {
            try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        @Override public String stableId(String value) { return value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
