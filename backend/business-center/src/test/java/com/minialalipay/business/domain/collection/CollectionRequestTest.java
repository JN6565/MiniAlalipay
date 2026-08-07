package com.minialalipay.business.domain.collection;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 固定金额收款请求领域规则测试。
 *
 * <p>一码多收模型下请求不再单笔占用：扫码与受理阶段只判断请求是否仍可收款，
 * 关闭仅允许在尚无任何支付受理（OPEN）时执行。</p>
 */
class CollectionRequestTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 尚无支付受理的请求可以关闭且重复关闭幂等() {
        CollectionRequest request = CollectionRequest.create("request-1", "payee-user", "payee-account",
                8_800L, "聚餐费用", NOW);

        request.close(0L, NOW.plusSeconds(1));

        assertEquals(CollectionRequestStatus.CANCELLED, request.getStatus());
        assertEquals(1L, request.getVersion());
        // 重复关闭返回既有终态，不改变版本，支持幂等重试
        request.close(1L, NOW.plusSeconds(2));
        assertEquals(1L, request.getVersion());
    }

    @Test
    void 过期或版本冲突的固定请求不能关闭() {
        CollectionRequest expired = CollectionRequest.create("request-1", "payee-user", "payee-account",
                8_800L, "聚餐费用", NOW);
        assertThrows(IllegalStateException.class,
                () -> expired.close(0L, NOW.plusSeconds(1800)));
        assertEquals(CollectionRequestStatus.EXPIRED, expired.getStatus());

        CollectionRequest stale = CollectionRequest.create("request-2", "payee-user", "payee-account",
                8_800L, "聚餐费用", NOW);
        assertThrows(IllegalStateException.class,
                () -> stale.close(1L, NOW.plusSeconds(1)));
        assertEquals(CollectionRequestStatus.OPEN, stale.getStatus());
    }

    @Test
    void 未到期请求不过期且到期后转为过期终态() {
        CollectionRequest request = CollectionRequest.create("request-1", "payee-user", "payee-account",
                8_800L, "聚餐费用", NOW);

        assertFalse(request.expireIfNecessary(NOW.plusSeconds(1799)));
        assertEquals(CollectionRequestStatus.OPEN, request.getStatus());

        assertTrue(request.expireIfNecessary(NOW.plusSeconds(1800)));
        assertEquals(CollectionRequestStatus.EXPIRED, request.getStatus());
    }
}
