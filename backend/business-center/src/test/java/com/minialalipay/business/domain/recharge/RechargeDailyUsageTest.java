package com.minialalipay.business.domain.recharge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RechargeDailyUsageTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final RechargePolicy POLICY = RechargePolicy.defaultActive("policy-1", NOW);

    @Test
    void 预占计入处理中额度并阻止超过日累计或次数() {
        RechargeDailyUsage usage = RechargeDailyUsage.empty("user-1", LocalDate.of(2026, 8, 5), NOW);

        usage.reserve(0L, 5_000_000L, POLICY, NOW.plusSeconds(1));
        usage.reserve(1L, 5_000_000L, POLICY, NOW.plusSeconds(2));
        usage.reserve(2L, 5_000_000L, POLICY, NOW.plusSeconds(3));
        usage.reserve(3L, 5_000_000L, POLICY, NOW.plusSeconds(4));
        usage.reserve(4L, 5_000_000L, POLICY, NOW.plusSeconds(5));

        assertEquals(25_000_000L, usage.getProcessingFen());
        assertEquals(5, usage.getProcessingCount());
        assertThrows(IllegalStateException.class,
                () -> usage.reserve(5L, 1L, POLICY, NOW.plusSeconds(6)));
    }

    @Test
    void 版本冲突和单笔越限不会改变用量渠道拒绝会释放预占() {
        RechargeDailyUsage usage = RechargeDailyUsage.empty("user-1", LocalDate.of(2026, 8, 5), NOW);

        assertThrows(IllegalStateException.class,
                () -> usage.reserve(1L, 100L, POLICY, NOW.plusSeconds(1)));
        assertThrows(IllegalStateException.class,
                () -> usage.reserve(0L, 5_000_001L, POLICY, NOW.plusSeconds(1)));
        usage.reserve(0L, 100L, POLICY, NOW.plusSeconds(2));
        usage.release(1L, 100L, NOW.plusSeconds(3));

        assertEquals(0L, usage.getProcessingFen());
        assertEquals(0, usage.getProcessingCount());
        assertEquals(2L, usage.getVersion());
    }
}
