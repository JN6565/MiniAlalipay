package com.minialalipay.business.domain.collection;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectionOrderTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 个人码订单仅允许绑定付款方锁定一次金额() {
        CollectionOrder order = CollectionOrder.forPersonalCode("order-1", "code-1", "payee-user", "payee-account",
                "payer-user", "payer-account", NOW);

        order.lockPersonalAmount("payer-user", 0L, 5_200L, "午餐", NOW.plusSeconds(1));

        assertEquals(CollectionOrderStatus.PENDING_CONFIRMATION, order.getStatus());
        assertEquals(5_200L, order.getAmountFen());
        assertThrows(IllegalStateException.class,
                () -> order.lockPersonalAmount("payer-user", 1L, 6_000L, "晚餐", NOW.plusSeconds(2)));
    }

    @Test
    void 固定请求订单保持金额不可变并禁止本人付款() {
        CollectionOrder order = CollectionOrder.forFixedRequest("order-1", "request-1", "payee-user", "payee-account",
                "payer-user", "payer-account", 8_800L, "聚餐费用", NOW);

        assertThrows(IllegalStateException.class,
                () -> order.lockPersonalAmount("payer-user", 0L, 8_800L, "修改金额", NOW.plusSeconds(1)));
        assertThrows(IllegalArgumentException.class,
                () -> CollectionOrder.forFixedRequest("order-2", "request-1", "payee-user", "payee-account",
                        "payee-user", "payee-account", 8_800L, "聚餐费用", NOW));
    }
}
