package com.minialalipay.business.interfaces.scenario;

import com.minialalipay.business.application.manualcase.ManualCaseApplicationService;
import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.ManualCaseStore;
import com.minialalipay.business.application.port.RechargeStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.recharge.RechargeApplicationService;
import com.minialalipay.business.domain.manualcase.ManualCase;
import com.minialalipay.business.domain.manualcase.ManualCaseStatus;
import com.minialalipay.business.domain.manualcase.ManualCaseType;
import com.minialalipay.business.domain.recharge.RechargeDailyUsage;
import com.minialalipay.business.domain.recharge.RechargeOrder;
import com.minialalipay.business.domain.recharge.RechargeOrderStatus;
import com.minialalipay.business.domain.recharge.RechargePolicy;
import com.minialalipay.business.interfaces.error.BusinessCenterExceptionHandler;
import com.minialalipay.business.interfaces.manualcase.ManualCaseController;
import com.minialalipay.business.interfaces.recharge.RechargeController;
import com.minialalipay.business.interfaces.security.OpsAccessGuard;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 充值和工单非资金接口的 MVC 切片测试。 */
class ScenarioControllerTest {
    private MockMvc mvc;
    private RechargeApplicationService recharges;
    private ManualCaseApplicationService manualCases;

    @BeforeEach
    void setUp() {
        recharges = rechargeService(new RechargeMemoryStore());
        manualCases = manualCaseService(new ManualCaseMemoryStore());
        RequestIdGenerator requestIds = new RequestIdGenerator();
        mvc = MockMvcBuilders.standaloneSetup(
                        new RechargeController(recharges, requestIds),
                        new ManualCaseController(manualCases, new OpsAccessGuard(), requestIds, new IdempotencyKeyValidator()))
                .setControllerAdvice(new BusinessCenterExceptionHandler(new CommonExceptionMapper(), requestIds))
                .build();
    }

    @Test
    void 充值创建与本人查询返回待渠道状态和请求编号() throws Exception {
        mvc.perform(post("/api/v1/recharges")
                        .header("X-User-Id", "user-1")
                        .header("X-Request-Id", "req-recharge-create")
                        .header("Idempotency-Key", "123e4567-e89b-12d3-a456-426614174000")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountFen\":100}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value("req-recharge-create"))
                .andExpect(jsonPath("$.data.status").value("PENDING_CHANNEL"));

        mvc.perform(get("/api/v1/recharges/id-1").header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rechargeOrderId").value("id-1"));
    }

    @Test
    void 工单查询允许运营人员而普通用户查询与处置均被拒绝() throws Exception {
        mvc.perform(get("/api/v1/manual-cases").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].caseId").value("case-1"));

        mvc.perform(get("/api/v1/manual-cases").header("X-User-Roles", "USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OPS_PERMISSION_REQUIRED"));

        mvc.perform(post("/api/v1/manual-cases/case-1/decisions")
                        .header("X-User-Id", "operator-1")
                        .header("X-User-Roles", "USER")
                        .header("Idempotency-Key", "123e4567-e89b-12d3-a456-426614174000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CLAIM\",\"version\":0}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OPS_PERMISSION_REQUIRED"));
    }

    private static RechargeApplicationService rechargeService(RechargeMemoryStore store) {
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-1", userId, "ACTIVE");
        return new RechargeApplicationService(store, accounts, new TestSecurity(), new IdempotencyKeyValidator(),
                null, null);
    }

    private static ManualCaseApplicationService manualCaseService(ManualCaseMemoryStore store) {
        return new ManualCaseApplicationService(store, new TestSecurity());
    }

    private static final class RechargeMemoryStore implements RechargeStore {
        private final RechargePolicy policy = RechargePolicy.defaultActive("policy-1", Instant.parse("2026-08-05T08:00:00Z"));
        private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();
        private final Map<String, RechargeOrder> orders = new HashMap<>();
        private RechargeDailyUsage usage;
        @Override public RechargePolicy getActivePolicy() { return policy; }
        @Override public Optional<RechargeDailyUsage> findDailyUsage(String userId, LocalDate date) { return Optional.ofNullable(usage); }
        @Override public Optional<IdempotencyRecord> findIdempotency(String userId, String key) { return Optional.ofNullable(idempotency.get(userId + key)); }
        @Override public boolean reserveIdempotency(String recordId, String userId, String key, byte[] hash, String orderId) {
            return idempotency.putIfAbsent(userId + key, new IdempotencyRecord(hash, orderId)) == null;
        }
        @Override public boolean createOrderAndUpdateUsage(RechargeOrder order, RechargeDailyUsage value, long expectedVersion) {
            usage = value; orders.put(order.getRechargeOrderId(), order); return true;
        }
        @Override public Optional<RechargeOrder> findOrder(String orderId) { return Optional.ofNullable(orders.get(orderId)); }
        @Override public boolean updateOrder(RechargeOrder order, long expectedVersion) {
            RechargeOrder current = orders.get(order.getRechargeOrderId());
            if (current == null || current.getVersion() != expectedVersion) return false;
            orders.put(order.getRechargeOrderId(), order);
            return true;
        }
    }

    private static final class ManualCaseMemoryStore implements ManualCaseStore {
        private final Map<String, DecisionIdempotencyRecord> idempotency = new HashMap<>();
        private ManualCase value = ManualCase.open("case-1", ManualCaseType.RISK_PRECHECK,
                "QR_PAY_ORDER", "order-1", "RISK_MANUAL_REVIEW", Instant.parse("2026-08-05T08:00:00Z"));
        @Override public List<ManualCase> list(String cursor, ManualCaseStatus status, ManualCaseType type, int limit) {
            return List.of(value);
        }
        @Override public Optional<ManualCase> find(String caseId) { return Optional.ofNullable(value); }
        @Override public boolean create(ManualCase manualCase) { value = manualCase; return true; }
        @Override public boolean update(ManualCase manualCase, long expectedVersion) { value = manualCase; return true; }
        @Override public Optional<DecisionIdempotencyRecord> findDecisionIdempotency(String operatorId, String key) {
            return Optional.ofNullable(idempotency.get(operatorId + key));
        }
        @Override public boolean reserveDecisionIdempotency(String recordId, String operatorId, String key, byte[] hash) {
            return idempotency.putIfAbsent(operatorId + key, new DecisionIdempotencyRecord(hash, null)) == null;
        }
        @Override public void completeDecisionIdempotency(String operatorId, String key, ManualCase manualCase) {
            DecisionIdempotencyRecord record = idempotency.get(operatorId + key);
            idempotency.put(operatorId + key, new DecisionIdempotencyRecord(record.requestHash(), manualCase));
        }
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int id;
        @Override public String newId() { return "id-" + ++id; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "unused"; }
        @Override public String newQrToken() { return "unused-qr"; }
        @Override public String newCollectionToken() { return "unused-collection"; }
        @Override public byte[] digest(String value) { return value.getBytes(StandardCharsets.UTF_8); }
        @Override public String stableId(String value) { return value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
