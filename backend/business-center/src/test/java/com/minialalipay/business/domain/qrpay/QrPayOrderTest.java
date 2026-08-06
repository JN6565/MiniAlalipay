package com.minialalipay.business.domain.qrpay;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QrPayOrderTest {

    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final QrTokenDigest TOKEN = QrTokenDigest.fromHex(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

    @Test
    void 同一会话重复交换令牌保持幂等且其他会话被拒绝() {
        QrPayOrder order = newOrder();

        order.exchangeToken("bootstrap-1", TOKEN, NOW.plusSeconds(1));
        order.exchangeToken("bootstrap-1", TOKEN, NOW.plusSeconds(2));

        assertThrows(IllegalStateException.class,
                () -> order.exchangeToken("bootstrap-2", TOKEN, NOW.plusSeconds(3)));
        assertEquals(QrPayOrderStatus.CREATED, order.getStatus());
        assertEquals("bootstrap-1", order.getBoundBootstrapSessionId());
    }

    @Test
    void 篡改或过期令牌不能交换且过期订单不再受理() {
        QrPayOrder order = newOrder();
        QrTokenDigest altered = QrTokenDigest.fromHex(
                "1123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertThrows(IllegalArgumentException.class,
                () -> order.exchangeToken("bootstrap-1", altered, NOW.plusSeconds(1)));
        assertThrows(IllegalStateException.class,
                () -> order.exchangeToken("bootstrap-1", TOKEN, NOW.plusSeconds(300)));
        assertEquals(QrPayOrderStatus.EXPIRED, order.getStatus());
    }

    @Test
    void 扫码和确认前锁定使用版本比较并阻止本人付款() {
        QrPayOrder order = newOrder();
        order.exchangeToken("bootstrap-1", TOKEN, NOW.plusSeconds(1));
        order.scan("bootstrap-1", 1L, NOW.plusSeconds(2));

        assertThrows(IllegalStateException.class,
                () -> order.lockPayer("payee-user", "payer-account", 2L, NOW.plusSeconds(3)));
        order.lockPayer("payer-user", "payer-account", 2L, NOW.plusSeconds(3));

        assertEquals(QrPayOrderStatus.PENDING_CONFIRMATION, order.getStatus());
        assertEquals(3L, order.getVersion());
        assertThrows(IllegalStateException.class,
                () -> order.markRiskReview(2L, NOW.plusSeconds(4)));
    }

    @Test
    void 风控人工复核与取消只限资金受理前状态() {
        QrPayOrder order = newOrder();
        order.exchangeToken("bootstrap-1", TOKEN, NOW.plusSeconds(1));
        order.scan("bootstrap-1", 1L, NOW.plusSeconds(2));
        order.lockPayer("payer-user", "payer-account", 2L, NOW.plusSeconds(3));

        order.markRiskReview(3L, NOW.plusSeconds(4));
        order.cancel(4L, NOW.plusSeconds(5));

        assertEquals(QrPayOrderStatus.CANCELLED, order.getStatus());
        assertThrows(IllegalStateException.class,
                () -> order.acceptByFundTransaction(5L, "tx-1", NOW.plusSeconds(6)));
    }

    private static QrPayOrder newOrder() {
        return QrPayOrder.create("qr-order-1", "payee-user", "payee-account", 8_800L,
                "演示商品", TOKEN, NOW);
    }
}
