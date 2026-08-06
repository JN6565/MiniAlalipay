package com.minialalipay.business.domain.collection;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectionRequestTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 首笔受理占用请求且其他订单无法重复占用() {
        CollectionRequest request = CollectionRequest.create("request-1", "payee-user", "payee-account",
                8_800L, "聚餐费用", NOW);

        request.reserveForOrder("order-1", 0L, NOW.plusSeconds(1));

        assertEquals(CollectionRequestStatus.RESERVED, request.getStatus());
        assertEquals("order-1", request.getActiveOrderId());
        assertThrows(IllegalStateException.class,
                () -> request.reserveForOrder("order-2", 1L, NOW.plusSeconds(2)));
        request.reserveForOrder("order-1", 1L, NOW.plusSeconds(2));
        assertEquals(1L, request.getVersion());
    }

    @Test
    void 过期或版本冲突的固定请求不能受理() {
        CollectionRequest request = CollectionRequest.create("request-1", "payee-user", "payee-account",
                8_800L, "聚餐费用", NOW);

        assertThrows(IllegalStateException.class,
                () -> request.reserveForOrder("order-1", 1L, NOW.plusSeconds(1)));
        assertThrows(IllegalStateException.class,
                () -> request.reserveForOrder("order-1", 0L, NOW.plusSeconds(1800)));
        assertEquals(CollectionRequestStatus.EXPIRED, request.getStatus());
    }
}
