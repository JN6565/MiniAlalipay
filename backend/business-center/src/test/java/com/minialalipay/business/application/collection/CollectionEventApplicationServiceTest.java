package com.minialalipay.business.application.collection;

import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.collection.CollectionOrderEvent;
import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 固定请求 SSE 游标、最小事件字段和终态关闭测试。 */
class CollectionEventApplicationServiceTest {
    private final CollectionApplicationService collections = mock(CollectionApplicationService.class);
    private final CollectionStore store = mock(CollectionStore.class);
    private final CollectionEventApplicationService service = new CollectionEventApplicationService(collections, store, new TestSecurity());
    private final CollectionRequest request = CollectionRequest.create("request-1", "payee-1", "account-payee-1", 5200L,
            "午餐", Instant.parse("2026-08-05T12:00:00Z"));

    @AfterEach
    void stopPoller() {
        service.shutdown();
    }

    @Test
    void 游标不在保留期内时拒绝补发而不猜测状态() {
        when(collections.getRequest("payee-1", "request-1")).thenReturn(request);
        when(store.findRequestEvent("request-1", "expired-event")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subscribeRequest("payee-1", "request-1", "expired-event"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.EVENT_CURSOR_EXPIRED));
    }

    @Test
    void 重连只从LastEventId之后读取并在终态关闭() {
        CollectionOrderEvent cursor = event("event-1", "PROCESSING");
        CollectionOrderEvent terminal = event("event-2", "SUCCESS");
        when(collections.getRequest("payee-1", "request-1")).thenReturn(request);
        when(store.findRequestEvent("request-1", "event-1")).thenReturn(Optional.of(cursor));
        when(store.findRequestEventsAfter("request-1", "event-1", 100)).thenReturn(List.of(terminal));

        var emitter = service.subscribeRequest("payee-1", "request-1", "event-1");

        assertThat(emitter).isNotNull();
        verify(store).findRequestEventsAfter("request-1", "event-1", 100);
    }

    @Test
    void 客户端已确认终态游标时立即结束连接() {
        CollectionOrderEvent terminal = event("event-2", "SUCCESS");
        when(collections.getRequest("payee-1", "request-1")).thenReturn(request);
        when(store.findRequestEvent("request-1", "event-2")).thenReturn(Optional.of(terminal));

        var emitter = service.subscribeRequest("payee-1", "request-1", "event-2");

        assertThat(emitter).isNotNull();
        org.mockito.Mockito.verify(store, org.mockito.Mockito.never()).findRequestEventsAfter(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void 公开事件不包含账户会话或任何令牌字段() {
        assertThat(CollectionOrderEvent.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("eventId", "requestId", "activeOrderId", "transactionId", "status", "occurredAt")
                .doesNotContain("payerAccountId", "payeeAccountId", "h5SessionId", "token", "confirmationToken", "paymentProof");
    }

    private static CollectionOrderEvent event(String eventId, String status) {
        return new CollectionOrderEvent(eventId, "request-1", "order-1", "transaction-1", status,
                Instant.parse("2026-08-05T12:00:01Z"));
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        @Override public String newId() { return "event-generated"; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "confirmation"; }
        @Override public String newQrToken() { return "qr"; }
        @Override public String newCollectionToken() { return "collection"; }
        @Override public byte[] digest(String value) { return value.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
        @Override public String stableId(String value) { return value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
