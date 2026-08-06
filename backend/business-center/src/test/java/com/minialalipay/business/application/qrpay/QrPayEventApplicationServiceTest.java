package com.minialalipay.business.application.qrpay;

import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderEvent;
import com.minialalipay.business.domain.qrpay.QrPayOrderStatus;
import com.minialalipay.business.domain.qrpay.QrTokenDigest;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 动态扫码 SSE 的首帧、断线续传、游标边界和敏感字段测试。 */
class QrPayEventApplicationServiceTest {
    private final QrPayApplicationService orders = mock(QrPayApplicationService.class);
    private final QrPayStore store = mock(QrPayStore.class);
    private final QrPayEventApplicationService service = new QrPayEventApplicationService(orders, store, new TestSecurity());

    @AfterEach
    void shutdown() {
        service.shutdown();
    }

    @Test
    void 首次订阅发送权威快照并持久化可续传游标() {
        QrPayOrder order = order(QrPayOrderStatus.PROCESSING, "transaction-1");
        when(orders.getForAuthorizedUser("payer-1", "session-1", "order-1")).thenReturn(order);
        when(store.findLatestOrderEvent("order-1")).thenReturn(Optional.empty());

        var emitter = service.subscribe("payer-1", "session-1", "order-1", null);

        assertThat(emitter).isNotNull();
        verify(store).appendOrderEvent(any(QrPayOrderEvent.class));
    }

    @Test
    void 游标过期时拒绝猜测状态() {
        when(orders.getForAuthorizedUser("payer-1", "session-1", "order-1"))
                .thenReturn(order(QrPayOrderStatus.PROCESSING, "transaction-1"));
        when(store.findOrderEvent("order-1", "expired-event")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.subscribe("payer-1", "session-1", "order-1", "expired-event"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.EVENT_CURSOR_EXPIRED));
    }

    @Test
    void 续传只读取游标之后事件并在终态关闭() {
        QrPayOrderEvent cursor = event("event-1", "PROCESSING");
        QrPayOrderEvent terminal = event("event-2", "SUCCESS");
        when(orders.getForAuthorizedUser("payer-1", "session-1", "order-1"))
                .thenReturn(order(QrPayOrderStatus.PROCESSING, "transaction-1"));
        when(store.findOrderEvent("order-1", "event-1")).thenReturn(Optional.of(cursor));
        when(store.findOrderEventsAfter("order-1", "event-1", 100)).thenReturn(List.of(terminal));

        var emitter = service.subscribe("payer-1", "session-1", "order-1", "event-1");

        assertThat(emitter).isNotNull();
        verify(store).findOrderEventsAfter("order-1", "event-1", 100);
    }

    @Test
    void SSE事件不暴露令牌会话账户或确认材料() {
        assertThat(QrPayOrderEvent.class.getRecordComponents()).extracting(component -> component.getName())
                .containsExactly("eventId", "qrOrderId", "transactionId", "status", "occurredAt")
                .doesNotContain("token", "h5SessionId", "payerAccountId", "payeeAccountId", "paymentProof", "confirmationToken");
    }

    private static QrPayOrder order(QrPayOrderStatus status, String transactionId) {
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        return new QrPayOrder("order-1", "payee-1", "account-payee-1", 100, "午餐",
                QrTokenDigest.fromHex("00".repeat(32)), status, "session-1", "payer-1", "account-payer-1",
                transactionId, 4, now.plusSeconds(300), now, now);
    }

    private static QrPayOrderEvent event(String eventId, String status) {
        return new QrPayOrderEvent(eventId, "order-1", "transaction-1", status, Instant.parse("2026-08-05T12:00:01Z"));
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        @Override public String newId() { return "snapshot-event"; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "confirmation"; }
        @Override public String newQrToken() { return "qr"; }
        @Override public String newCollectionToken() { return "collection"; }
        @Override public byte[] digest(String value) { return value.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
        @Override public String stableId(String value) { return value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
