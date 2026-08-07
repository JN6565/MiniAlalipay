package com.minialalipay.business.application.collection;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.PersonalCollectionCode;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C2C 令牌交换与请求仲裁应用服务测试：每次扫码新建订单、永不复用、一码多收。 */
class CollectionApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 登录付款人交换个人码后只能锁定一次金额() {
        CollectionStore store = mock(CollectionStore.class);
        TestSecurity security = new TestSecurity();
        PersonalCollectionCode code = PersonalCollectionCode.activate("code-1", "payee-1", "account-payee-1", NOW);
        AtomicReference<CollectionOrder> stored = new AtomicReference<>();
        when(store.findOrderByBootstrapSessionId(anyString())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(store.findActiveCodeByTokenDigest(any())).thenReturn(Optional.of(code));
        when(store.createPersonalOrder(any(), anyString())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return true;
        });
        when(store.updateOrder(any(), anyLong())).thenReturn(true);
        CollectionApplicationService service = service(store, security);

        var order = service.exchange("payer-1", "session-1", "token-0123456789abcdef");
        var locked = service.lockPersonalOrder("payer-1", "session-1", order.getOrderId(), 0L, 5200L, "午餐");

        assertThat(locked.getStatus().name()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(locked.getAmountFen()).isEqualTo(5200L);
        verify(store).createPersonalOrder(any(), anyString());
        verify(store).updateOrder(any(), org.mockito.ArgumentMatchers.eq(0L));
    }

    @Test
    void 固定请求建单失败时不产生伪成功订单() {
        CollectionStore store = mock(CollectionStore.class);
        TestSecurity security = new TestSecurity();
        CollectionRequest request = CollectionRequest.create("request-1", "payee-1", "account-payee-1", 8800L, "聚餐", NOW);
        when(store.findOrderByBootstrapSessionId(anyString())).thenReturn(Optional.empty());
        when(store.findActiveCodeByTokenDigest(any())).thenReturn(Optional.empty());
        when(store.findRequestByTokenDigest(any())).thenReturn(Optional.of(request));
        when(store.createFixedOrder(any(), anyString())).thenReturn(false);
        CollectionApplicationService service = service(store, security);

        assertThatThrownBy(() -> service.exchange("payer-2", "session-2", "token-0123456789abcdef"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.COLLECTION_REQUEST_PROCESSING));
    }

    @Test
    void 同一会话连扫不同收款码每次都新建订单且旧订单解绑() {
        CollectionStore store = mock(CollectionStore.class);
        TestSecurity security = new TestSecurity();
        PersonalCollectionCode code = PersonalCollectionCode.activate("code-1", "payee-1", "account-payee-1", NOW);
        CollectionRequest request = CollectionRequest.create("request-1", "payee-2", "account-payee-2", 8800L, "聚餐", NOW);
        AtomicReference<CollectionOrder> stored = new AtomicReference<>();
        when(store.findOrderByBootstrapSessionId(anyString())).thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        // 个人码与固定请求使用不同令牌：按令牌摘要分别命中两条分支
        when(store.findActiveCodeByTokenDigest(any())).thenAnswer(invocation -> {
            byte[] digest = invocation.getArgument(0);
            return "token-personal-code".equals(new String(digest, StandardCharsets.UTF_8)) ? Optional.of(code) : Optional.empty();
        });
        when(store.findRequestByTokenDigest(any())).thenAnswer(invocation -> {
            byte[] digest = invocation.getArgument(0);
            return "token-fixed-request".equals(new String(digest, StandardCharsets.UTF_8)) ? Optional.of(request) : Optional.empty();
        });
        when(store.createPersonalOrder(any(), anyString())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return true;
        });
        when(store.createFixedOrder(any(), anyString())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return true;
        });
        CollectionApplicationService service = service(store, security);

        // 第一次扫个人码得到草稿订单
        CollectionOrder first = service.exchange("payer-1", "session-1", "token-personal-code");
        assertThat(first.getPersonalCodeId()).isEqualTo("code-1");
        // 第二次扫固定码：旧订单必须先解绑，并新建金额来自固定请求的独立订单，绝不复用旧单金额
        CollectionOrder second = service.exchange("payer-1", "session-1", "token-fixed-request");

        assertThat(second.getOrderId()).isNotEqualTo(first.getOrderId());
        assertThat(second.getRequestId()).isEqualTo("request-1");
        assertThat(second.getAmountFen()).isEqualTo(8800L);
        assertThat(second.getStatus().name()).isEqualTo("PENDING_CONFIRMATION");
        verify(store).clearSessionBinding(first.getOrderId());
    }

    @Test
    void 多个付款人扫同一固定码各得独立新订单() {
        CollectionStore store = mock(CollectionStore.class);
        TestSecurity security = new TestSecurity();
        CollectionRequest request = CollectionRequest.create("request-1", "payee-1", "account-payee-1", 8800L, "聚餐", NOW);
        when(store.findOrderByBootstrapSessionId(anyString())).thenReturn(Optional.empty());
        when(store.findActiveCodeByTokenDigest(any())).thenReturn(Optional.empty());
        when(store.findRequestByTokenDigest(any())).thenReturn(Optional.of(request));
        when(store.createFixedOrder(any(), anyString())).thenReturn(true);
        CollectionApplicationService service = service(store, security);

        CollectionOrder first = service.exchange("payer-1", "session-1", "token-0123456789abcdef");
        CollectionOrder second = service.exchange("payer-2", "session-2", "token-0123456789abcdef");

        // 一码多收：扫码阶段不占用，两个付款人各自得到独立订单，均可进入后续支付受理
        assertThat(first.getOrderId()).isNotEqualTo(second.getOrderId());
        assertThat(first.getPayerUserId()).isEqualTo("payer-1");
        assertThat(second.getPayerUserId()).isEqualTo("payer-2");
        assertThat(request.getStatus().name()).isEqualTo("OPEN");
        verify(store, atLeastOnce()).createFixedOrder(any(), anyString());
    }

    @Test
    void 已取消或已过期的固定请求不能被交换建单() {
        CollectionStore store = mock(CollectionStore.class);
        TestSecurity security = new TestSecurity();
        CollectionRequest cancelled = CollectionRequest.create("request-1", "payee-1", "account-payee-1", 8800L, "聚餐", NOW);
        cancelled.close(0L, NOW.plusSeconds(1));
        when(store.findOrderByBootstrapSessionId(anyString())).thenReturn(Optional.empty());
        when(store.findActiveCodeByTokenDigest(any())).thenReturn(Optional.empty());
        when(store.findRequestByTokenDigest(any())).thenReturn(Optional.of(cancelled));
        CollectionApplicationService service = service(store, security);

        assertThatThrownBy(() -> service.exchange("payer-1", "session-1", "token-0123456789abcdef"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.COLLECTION_REQUEST_CANCELLED));
    }

    @Test
    void 仅请求创建者可读取该请求的订单列表() {
        CollectionStore store = mock(CollectionStore.class);
        TestSecurity security = new TestSecurity();
        CollectionRequest request = CollectionRequest.create("request-1", "payee-1", "account-payee-1", 8800L, "聚餐", NOW);
        CollectionOrder order = CollectionOrder.forFixedRequest("order-1", "request-1", "payee-1", "account-payee-1",
                "payer-1", "account-payer-1", 8800L, "聚餐", NOW);
        when(store.findRequest("request-1")).thenReturn(Optional.of(request));
        when(store.findOrdersByRequestId("request-1")).thenReturn(java.util.List.of(order));
        CollectionApplicationService service = service(store, security);

        assertThat(service.getRequestOrders("payee-1", "request-1")).hasSize(1);
        assertThatThrownBy(() -> service.getRequestOrders("payer-1", "request-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.REQUEST_NOT_FOUND));
    }

    private static CollectionApplicationService service(CollectionStore store, SecurityMaterialPort security) {
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-" + userId, userId, "ACTIVE");
        return new CollectionApplicationService(store, accounts, security, new IdempotencyKeyValidator(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int sequence;
        @Override public String newId() { return "order-" + ++sequence; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "confirmation"; }
        @Override public String newQrToken() { return "qr"; }
        @Override public String newCollectionToken() { return "collection"; }
        @Override public byte[] digest(String value) { return value.getBytes(StandardCharsets.UTF_8); }
        @Override public String stableId(String value) { return "session-" + value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
