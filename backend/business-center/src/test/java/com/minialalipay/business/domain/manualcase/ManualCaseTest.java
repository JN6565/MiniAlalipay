package com.minialalipay.business.domain.manualcase;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManualCaseTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 工单领取处理恢复与关闭需要版本及证据() {
        ManualCase manualCase = ManualCase.open("case-1", ManualCaseType.RISK_PRECHECK,
                "QR_PAY_ORDER", "qr-1", "RISK_MANUAL_REVIEW", NOW);

        manualCase.claim("operator-1", 0L, NOW.plusSeconds(1));
        assertThrows(IllegalArgumentException.class,
                () -> manualCase.resolve("operator-1", 1L, "通过", null, NOW.plusSeconds(2)));
        manualCase.resolve("operator-1", 1L, "通过", "evidence-1", NOW.plusSeconds(2));
        manualCase.reopen("operator-1", 2L, "需补充核验", NOW.plusSeconds(3));
        manualCase.close("operator-1", 3L, "复核结束", "evidence-2", NOW.plusSeconds(4));

        assertEquals(ManualCaseStatus.CLOSED, manualCase.getStatus());
        assertEquals(4L, manualCase.getVersion());
    }

    @Test
    void 非领取人不能处理工单且关闭后不能恢复() {
        ManualCase manualCase = ManualCase.open("case-1", ManualCaseType.RISK_PRECHECK,
                "QR_PAY_ORDER", "qr-1", "RISK_MANUAL_REVIEW", NOW);
        manualCase.claim("operator-1", 0L, NOW.plusSeconds(1));

        assertThrows(IllegalStateException.class,
                () -> manualCase.resolve("operator-2", 1L, "通过", "evidence-1", NOW.plusSeconds(2)));
    }
}
