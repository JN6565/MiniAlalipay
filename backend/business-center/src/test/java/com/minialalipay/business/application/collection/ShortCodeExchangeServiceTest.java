package com.minialalipay.business.application.collection;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.collection.PersonalCollectionCode;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrTokenDigest;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 手动输入短码兑换应用服务测试：三类码等价于扫码、失效一律呈现短码无效。 */
class ShortCodeExchangeServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final QrTokenDigest QR_DIGEST = QrTokenDigest.fromHex(HexFormat.of().formatHex(new byte[32]));

    @Test
    void 个人码短码兑换等价于扫码并新建草稿订单() {
        CollectionStore store = mock(CollectionStore.class);
        PersonalCollectionCode code = PersonalCollectionCode.activate("code-1", "payee-1", "account-payee-1", NOW);
        AtomicReference<CollectionOrder> stored = new AtomicReference<>();
        when(store.findOrderByBootstrapSessionId(anyString())).thenReturn(Optional.empty());
        when(store.findActiveCodeByShortCode("12345678")).thenReturn(Optional.of(code));
        when(store.createPersonalOrder(any(), anyString())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return true;
        });
        ShortCodeExchangeService service = service(store, mock(QrPayStore.class));

        ShortCodeExchangeService.ExchangeResult result = service.exchange("payer-1", "session-1", "12345678");

        assertThat(result.codeType()).isEqualTo(ShortCodeExchangeService.CodeType.PERSONAL_CODE);
        assertThat(result.orderId()).isEqualTo(stored.get().getOrderId());
        assertThat(stored.get().getPersonalCodeId()).isEqualTo("code-1");
        assertThat(stored.get().getPayerUserId()).isEqualTo("payer-1");
    }

    @Test
    void 固定请求短码兑换复制请求金额并新建订单() {
        CollectionStore store = mock(CollectionStore.class);
        CollectionRequest request = CollectionRequest.create("request-1", "payee-1", "account-payee-1", 8800L, "聚餐", NOW);
        AtomicReference<CollectionOrder> stored = new AtomicReference<>();
        when(store.findOrderByBootstrapSessionId(anyString())).thenReturn(Optional.empty());
        when(store.findActiveCodeByShortCode(anyString())).thenReturn(Optional.empty());
        when(store.findRequestByShortCode("23456789")).thenReturn(Optional.of(request));
        when(store.createFixedOrder(any(), anyString())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return true;
        });
        ShortCodeExchangeService service = service(store, mock(QrPayStore.class));

        ShortCodeExchangeService.ExchangeResult result = service.exchange("payer-2", "session-2", "23456789");

        assertThat(result.codeType()).isEqualTo(ShortCodeExchangeService.CodeType.COLLECTION_REQUEST);
        assertThat(stored.get().getRequestId()).isEqualTo("request-1");
        assertThat(stored.get().getAmountFen()).isEqualTo(8800L);
    }

    @Test
    void 动态订单短码兑换把既有订单绑定到当前会话() {
        QrPayStore qrStore = mock(QrPayStore.class);
        QrPayOrder order = QrPayOrder.create("qr-1", "payee-1", "account-payee-1", 8800L, "动态收款", QR_DIGEST, NOW);
        when(qrStore.findByShortCode("34567890")).thenReturn(Optional.of(order));
        when(qrStore.update(any(), anyLong())).thenReturn(true);
        ShortCodeExchangeService service = service(mock(CollectionStore.class), qrStore);

        ShortCodeExchangeService.ExchangeResult result = service.exchange("payer-3", "session-3", "34567890");

        assertThat(result.codeType()).isEqualTo(ShortCodeExchangeService.CodeType.QR_PAY_ORDER);
        assertThat(result.orderId()).isEqualTo("qr-1");
        assertThat(order.getBoundBootstrapSessionId()).isNotNull();
    }

    @Test
    void 已被其他会话消费的动态订单短码按无效处理() {
        QrPayStore qrStore = mock(QrPayStore.class);
        QrPayOrder order = QrPayOrder.create("qr-1", "payee-1", "account-payee-1", 8800L, "动态收款", QR_DIGEST, NOW);
        order.exchangeToken("session-first", QR_DIGEST, NOW);
        when(qrStore.findByShortCode("34567890")).thenReturn(Optional.of(order));
        ShortCodeExchangeService service = service(mock(CollectionStore.class), qrStore);

        assertThatThrownBy(() -> service.exchange("payer-3", "session-other", "34567890"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.SHORT_CODE_INVALID));
    }

    @Test
    void 已过期固定请求的短码按无效处理并持久化过期终态() {
        CollectionStore store = mock(CollectionStore.class);
        CollectionRequest request = CollectionRequest.create("request-1", "payee-1", "account-payee-1", 8800L, "聚餐", NOW);
        when(store.findOrderByBootstrapSessionId(anyString())).thenReturn(Optional.empty());
        when(store.findActiveCodeByShortCode(anyString())).thenReturn(Optional.empty());
        when(store.findRequestByShortCode("23456789")).thenReturn(Optional.of(request));
        when(store.updateRequest(any(), anyLong())).thenReturn(true);
        // 时钟超过固定请求 30 分钟有效期
        ShortCodeExchangeService service = service(store, mock(QrPayStore.class), NOW.plus(Duration.ofMinutes(31)));

        assertThatThrownBy(() -> service.exchange("payer-2", "session-2", "23456789"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.SHORT_CODE_INVALID));
        assertThat(request.getStatus().name()).isEqualTo("EXPIRED");
    }

    @Test
    void 未知短码与非法格式一律返回短码无效() {
        ShortCodeExchangeService service = service(mock(CollectionStore.class), mock(QrPayStore.class));

        assertThatThrownBy(() -> service.exchange("payer-1", "session-1", "99999999"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.SHORT_CODE_INVALID));
        assertThatThrownBy(() -> service.exchange("payer-1", "session-1", "12ab5678"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.SHORT_CODE_INVALID));
    }

    @Test
    void 付款人即收款人时禁止兑换() {
        CollectionStore store = mock(CollectionStore.class);
        PersonalCollectionCode code = PersonalCollectionCode.activate("code-1", "payee-1", "account-payee-1", NOW);
        when(store.findOrderByBootstrapSessionId(anyString())).thenReturn(Optional.empty());
        when(store.findActiveCodeByShortCode("12345678")).thenReturn(Optional.of(code));
        ShortCodeExchangeService service = service(store, mock(QrPayStore.class));

        assertThatThrownBy(() -> service.exchange("payee-1", "session-1", "12345678"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(BusinessErrorCode.SELF_PAYMENT_FORBIDDEN));
    }

    private static ShortCodeExchangeService service(CollectionStore collectionStore, QrPayStore qrStore) {
        return service(collectionStore, qrStore, NOW);
    }

    private static ShortCodeExchangeService service(CollectionStore collectionStore, QrPayStore qrStore, Instant now) {
        AccountDirectoryPort accounts = userId -> new AccountDirectoryPort.AccountReference("account-" + userId, userId, "ACTIVE");
        SecurityMaterialPort security = new TestSecurity();
        return new ShortCodeExchangeService(collectionStore, qrStore, accounts, security, Clock.fixed(now, ZoneOffset.UTC));
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
