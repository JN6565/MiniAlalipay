package com.minialalipay.business.application.recharge;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.RechargeStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.domain.recharge.RechargeDailyUsage;
import com.minialalipay.business.domain.recharge.RechargeOrder;
import com.minialalipay.business.domain.recharge.RechargeOrderStatus;
import com.minialalipay.business.domain.recharge.RechargePolicy;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RechargeApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void 同键同参返回既有待渠道订单且只预占一次() {
        MemoryStore store = new MemoryStore();
        RechargeApplicationService service = service(store);

        RechargeOrder first = service.create("user-1", 100L, KEY);
        RechargeOrder repeated = service.create("user-1", 100L, KEY);

        assertEquals(first.getRechargeOrderId(), repeated.getRechargeOrderId());
        assertEquals(100L, store.usage.getProcessingFen());
        assertEquals(1, store.usage.getProcessingCount());
    }

    @Test
    void 同键异参或超过日限额均被拒绝() {
        MemoryStore store = new MemoryStore();
        RechargeApplicationService service = service(store);
        service.create("user-1", 100L, KEY);

        assertThrows(BusinessException.class, () -> service.create("user-1", 200L, KEY));
        service.create("user-1", 5_000_000L, "123e4567-e89b-12d3-a456-426614174001");
        service.create("user-1", 5_000_000L, "123e4567-e89b-12d3-a456-426614174002");
        service.create("user-1", 5_000_000L, "123e4567-e89b-12d3-a456-426614174003");
        service.create("user-1", 5_000_000L, "123e4567-e89b-12d3-a456-426614174004");
        assertThrows(BusinessException.class,
                () -> service.create("user-1", 5_000_000L, "123e4567-e89b-12d3-a456-426614174005"));
    }

    private static RechargeApplicationService service(MemoryStore store) {
        return service(store, null, null);
    }

    private static RechargeApplicationService service(MemoryStore store, BusinessStore businessStore, TccCoordinatorPort coordinator) {
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-1", userId, "ACTIVE");
        SecurityMaterialPort secure = new TestSecurity();
        return new RechargeApplicationService(store, accounts, secure, new IdempotencyKeyValidator(),
                businessStore, coordinator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 渠道成功后订单进入处理中并创建RECHARGE统一交易() {
        MemoryStore store = new MemoryStore();
        BusinessStore businessStore = mock(BusinessStore.class);
        TccCoordinatorPort coordinator = mock(TccCoordinatorPort.class);
        RechargeApplicationService service = service(store, businessStore, coordinator);
        RechargeOrder created = service.create("user-1", 100L, KEY);

        RechargeOrder processed = service.onChannelResult(created.getRechargeOrderId(), true, null, "0".repeat(32));

        assertEquals(RechargeOrderStatus.PROCESSING, processed.getStatus());
        assertNotNull(processed.getTransactionId());
        verify(businessStore).createTransaction(any(FundTransaction.class), any(), anyString(), any());
        verify(coordinator).startOrResume(any(FundTransaction.class));
    }

    @Test
    void 渠道拒绝后订单进入拒绝终态且重复回调幂等() {
        MemoryStore store = new MemoryStore();
        RechargeApplicationService service = service(store);
        RechargeOrder created = service.create("user-1", 100L, KEY);

        RechargeOrder rejected = service.onChannelResult(created.getRechargeOrderId(), false, "CHANNEL_TIMEOUT", null);

        assertEquals(RechargeOrderStatus.REJECTED, rejected.getStatus());
        assertEquals("CHANNEL_TIMEOUT", rejected.getRejectReasonCode());
        RechargeOrder replay = service.onChannelResult(created.getRechargeOrderId(), false, "CHANNEL_TIMEOUT", null);
        assertEquals(RechargeOrderStatus.REJECTED, replay.getStatus());
        assertEquals("CHANNEL_TIMEOUT", replay.getRejectReasonCode());
    }

    @Test
    void 已受理订单重复回调保持幂等且不重复创建交易() {
        MemoryStore store = new MemoryStore();
        BusinessStore businessStore = mock(BusinessStore.class);
        RechargeApplicationService service = service(store, businessStore, mock(TccCoordinatorPort.class));
        RechargeOrder created = service.create("user-1", 100L, KEY);
        service.onChannelResult(created.getRechargeOrderId(), true, null, "0".repeat(32));

        RechargeOrder replay = service.onChannelResult(created.getRechargeOrderId(), true, null, "0".repeat(32));

        assertEquals(RechargeOrderStatus.PROCESSING, replay.getStatus());
        verify(businessStore, times(1)).createTransaction(any(FundTransaction.class), any(), anyString(), any());
    }

    private static final class MemoryStore implements RechargeStore {
        private final RechargePolicy policy = RechargePolicy.defaultActive("policy-1", NOW);
        private final Map<String, IdempotencyRecord> idempotency = new HashMap<>();
        private final Map<String, RechargeOrder> orders = new HashMap<>();
        private RechargeDailyUsage usage;

        @Override public RechargePolicy getActivePolicy() { return policy; }
        @Override public Optional<RechargeDailyUsage> findDailyUsage(String userId, LocalDate businessDate) {
            return Optional.ofNullable(usage);
        }
        @Override public Optional<IdempotencyRecord> findIdempotency(String userId, String idempotencyKey) {
            return Optional.ofNullable(idempotency.get(userId + ":" + idempotencyKey));
        }
        @Override public boolean reserveIdempotency(String recordId, String userId, String idempotencyKey, byte[] requestHash,
                                                    String rechargeOrderId) {
            return idempotency.putIfAbsent(userId + ":" + idempotencyKey,
                    new IdempotencyRecord(requestHash, rechargeOrderId)) == null;
        }
        @Override public boolean createOrderAndUpdateUsage(RechargeOrder order, RechargeDailyUsage updated,
                                                            long expectedUsageVersion) {
            usage = updated;
            orders.put(order.getRechargeOrderId(), order);
            return true;
        }
        @Override public Optional<RechargeOrder> findOrder(String rechargeOrderId) {
            // 模拟数据库快照：返回拷贝，避免服务层原地变更污染持久化版本比较。
            return Optional.ofNullable(orders.get(rechargeOrderId)).map(MemoryStore::copy);
        }
        @Override public boolean updateOrder(RechargeOrder order, long expectedVersion) {
            RechargeOrder current = orders.get(order.getRechargeOrderId());
            if (current == null || current.getVersion() != expectedVersion) return false;
            orders.put(order.getRechargeOrderId(), order);
            return true;
        }
        private static RechargeOrder copy(RechargeOrder order) {
            return new RechargeOrder(order.getRechargeOrderId(), order.getUserId(), order.getTargetAccountId(),
                    order.getAmountFen(), order.getBusinessDate(), order.getPolicyId(), order.getPolicyVersion(),
                    order.getStatus(), order.getTransactionId(), order.getRejectReasonCode(), order.getVersion(),
                    order.getCreatedAt(), order.getUpdatedAt());
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
