package com.minialalipay.business.interfaces.collection;

import com.minialalipay.business.application.collection.CollectionApplicationService;
import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.collection.PersonalCollectionCode;
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
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** C2C Controller 切片测试，验证引导会话、登录付款人派生和敏感字段拒绝。 */
class CollectionControllerTest {
    private static final String TOKEN = "collection-token-0123456789abcdef";
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        MemoryStore store = new MemoryStore();
        TestSecurity security = new TestSecurity();
        store.codeByDigest.put(java.util.HexFormat.of().formatHex(security.digest(TOKEN)), PersonalCollectionCode.activate(
                "code-1", "payee-1", "account-payee-1", Instant.parse("2026-08-05T08:00:00Z")));
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-" + userId, userId, "ACTIVE");
        CollectionApplicationService service = new CollectionApplicationService(store, accounts, security, new IdempotencyKeyValidator());
        RequestIdGenerator requestIds = new RequestIdGenerator();
        mvc = MockMvcBuilders.standaloneSetup(new CollectionController(service, requestIds))
                .setControllerAdvice(new BusinessCenterExceptionHandler(new CommonExceptionMapper(), requestIds))
                .build();
    }

    @Test
    void 匿名引导只建立无业务数据会话并声明禁止缓存和引用来源() throws Exception {
        mvc.perform(get("/api/v1/p2p-collections/by-token").param("t", TOKEN))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void 令牌交换要求登录同一会话并拒绝客户端伪造账户字段() throws Exception {
        MockHttpSession session = new MockHttpSession(null, "bootstrap-1");
        mvc.perform(post("/api/v1/p2p-collections/token-exchanges").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/p2p-collections/token-exchanges").session(session)
                        .header("X-User-Id", "payer-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\",\"payerAccountId\":\"forged\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 登录付款人同会话交换后仅能一次锁定个人码金额() throws Exception {
        MockHttpSession session = new MockHttpSession(null, "bootstrap-2");
        mvc.perform(post("/api/v1/p2p-collections/token-exchanges").session(session)
                        .header("X-User-Id", "payer-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.collectionOrderId").value("order-1"))
                .andExpect(jsonPath("$.data.payerAccountId").doesNotExist())
                .andExpect(jsonPath("$.data.payeeAccountId").doesNotExist());

        mvc.perform(patch("/api/v1/p2p-collections/orders/order-1").session(session)
                        .header("X-User-Id", "payer-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"amountFen\":5200,\"subject\":\"午餐\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"));

        mvc.perform(patch("/api/v1/p2p-collections/orders/order-1").session(session)
                        .header("X-User-Id", "payer-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"amountFen\":5200,\"subject\":\"重复锁定\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void 订单查询只允许付款人或收款人读取而旁观用户不可感知存在() throws Exception {
        MockHttpSession session = new MockHttpSession(null, "bootstrap-3");
        mvc.perform(post("/api/v1/p2p-collections/token-exchanges").session(session)
                        .header("X-User-Id", "payer-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + TOKEN + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/p2p-collections/orders/order-1").header("X-User-Id", "payer-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/p2p-collections/orders/order-1").header("X-User-Id", "payee-1"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/p2p-collections/orders/order-1").header("X-User-Id", "observer-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    private static final class MemoryStore implements CollectionStore {
        private final Map<String, PersonalCollectionCode> codeByDigest = new HashMap<>();
        private final Map<String, CollectionOrder> orderBySession = new HashMap<>();

        @Override public Optional<PersonalCollectionCode> findActiveCode(String userId) { return Optional.empty(); }
        @Override public Optional<PersonalCollectionCode> findCode(String codeId) { return Optional.empty(); }
        @Override public Optional<PersonalCollectionCode> findActiveCodeByTokenDigest(byte[] tokenDigest) {
            return Optional.ofNullable(codeByDigest.get(hex(tokenDigest)));
        }
        @Override public boolean replaceCode(PersonalCollectionCode oldCode, PersonalCollectionCode newCode, byte[] tokenDigest, String recordId, String userId, String idempotencyKey, byte[] requestDigest) { return false; }
        @Override public boolean updateCode(PersonalCollectionCode code, long expectedVersion) { return false; }
        @Override public Optional<CollectionRequest> findRequest(String requestId) { return Optional.empty(); }
        @Override public Optional<CollectionRequest> findRequestByTokenDigest(byte[] tokenDigest) { return Optional.empty(); }
        @Override public Optional<CollectionOrder> findOrderByBootstrapSessionId(String sessionId) { return Optional.ofNullable(orderBySession.get(sessionId)); }
        @Override public Optional<CollectionOrder> findOrder(String orderId) {
            return orderBySession.values().stream().filter(value -> value.getOrderId().equals(orderId)).findFirst();
        }
        @Override public boolean createPersonalOrder(CollectionOrder order, String sessionId) {
            return orderBySession.putIfAbsent(sessionId, order) == null;
        }
        @Override public boolean reserveRequestAndCreateOrder(CollectionRequest request, long expectedVersion, CollectionOrder order, String sessionId) { return false; }
        @Override public boolean updateOrder(CollectionOrder order, long expectedVersion) { return true; }
        @Override public void clearSessionBinding(String orderId) { }
        @Override public boolean createRequest(CollectionRequest request, byte[] tokenDigest, String recordId, String userId, String idempotencyKey, byte[] requestDigest) { return false; }
        @Override public boolean updateRequest(CollectionRequest request, long expectedVersion) { return false; }
        @Override public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String key) { return Optional.empty(); }
        @Override public boolean reserveIdempotency(String recordId, String principal, String operation, String key, byte[] requestDigest, String resourceId, String resourceType) { return false; }
        private static String hex(byte[] value) { return java.util.HexFormat.of().formatHex(value); }
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int sequence;
        @Override public String newId() { return "order-" + ++sequence; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "confirmation"; }
        @Override public String newQrToken() { return "qr"; }
        @Override public String newCollectionToken() { return "collection"; }
        @Override public byte[] digest(String value) {
            try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        @Override public String stableId(String value) { return value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
