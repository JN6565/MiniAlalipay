package com.minialalipay.business.domain.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AlertTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 告警按确认恢复关闭流转并保留处置理由() {
        Alert alert = Alert.open("alert-1", "TCC_TIMEOUT", "P0", NOW);
        alert.acknowledge("operator-1", 0L, "开始处置", NOW.plusSeconds(1));
        alert.resolve("operator-1", 1L, "已恢复", NOW.plusSeconds(2));
        alert.close("operator-1", 2L, "观察完成", NOW.plusSeconds(3));

        assertEquals(AlertStatus.CLOSED, alert.getStatus());
        assertEquals("观察完成", alert.getLastReason());
    }

    @Test
    void 已恢复告警可以重开且非法关闭被拒绝() {
        Alert alert = Alert.open("alert-1", "TCC_TIMEOUT", "P0", NOW);
        assertThrows(IllegalStateException.class, () -> alert.close("operator-1", 0L, "关闭", NOW.plusSeconds(1)));
        alert.acknowledge("operator-1", 0L, "开始处置", NOW.plusSeconds(1));
        alert.resolve("operator-1", 1L, "已恢复", NOW.plusSeconds(2));
        alert.reopen(2L, "再次触发", NOW.plusSeconds(3));
        assertEquals(AlertStatus.OPEN, alert.getStatus());
    }
}
