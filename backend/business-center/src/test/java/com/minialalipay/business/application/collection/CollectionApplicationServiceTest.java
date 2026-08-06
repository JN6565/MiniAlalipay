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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C2C 令牌交换与请求仲裁应用服务测试。 */
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
    void 固定请求CAS失败时不把第二个付款人伪装为成功订单() {
        CollectionStore store = mock(CollectionStore.class);
        TestSecurity security = new TestSecurity();
        CollectionRequest request = CollectionRequest.create("request-1", "payee-1", "account-payee-1", 8800L, "聚餐", NOW);
        when(store.findOrderByBootstrapSessionId(anyString())).thenReturn(Optional.empty());
        when(store.findActiveCodeByTokenDigest(any())).thenReturn(Optional.empty());
        when(store.findRequestByTokenDigest(any())).thenReturn(Optional.of(request));
        when(store.reserveRequestAndCreateOrder(any(), anyLong(), any(), anyString())).thenReturn(false);
        CollectionApplicationService service = service(store, security);

        assertThatThrownBy(() -> service.exchange("payer-2", "session-2", "token-0123456789abcdef"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.COLLECTION_REQUEST_PROCESSING));
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
