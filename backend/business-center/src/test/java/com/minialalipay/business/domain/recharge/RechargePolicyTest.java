package com.minialalipay.business.domain.recharge;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RechargePolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 默认策略与已确认限额保持一致() {
        RechargePolicy policy = RechargePolicy.defaultActive("policy-1", NOW);

        assertEquals(5_000_000L, policy.getSingleLimitFen());
        assertEquals(25_000_000L, policy.getDailyLimitFen());
        assertEquals(5, policy.getDailyCountLimit());
    }

    @Test
    void 限额策略不能使用非法金额或次数() {
        assertThrows(IllegalArgumentException.class,
                () -> RechargePolicy.active("policy-1", 0L, 25_000_000L, 5, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> RechargePolicy.active("policy-1", 5_000_000L, 25_000_000L, 0, NOW));
    }
}
