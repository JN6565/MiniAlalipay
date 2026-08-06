package com.minialalipay.business.domain.risk;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RiskDecisionTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 人工复核决策不能关联已创建的资金交易() {
        assertThrows(IllegalArgumentException.class,
                () -> RiskDecision.manualReview("risk-1", "QR_PAY_ORDER", "qr-1", "tx-1", "v1", "RISK_HIGH", NOW));
    }

    @Test
    void 通过和拒绝决策保留不可变审核事实() {
        RiskDecision pass = RiskDecision.pass("risk-1", "QR_PAY_ORDER", "qr-1", "v1", "RISK_LOW", NOW);
        RiskDecision reject = RiskDecision.reject("risk-2", "QR_PAY_ORDER", "qr-1", "v1", "RISK_HIGH", "RISK_REJECTED", NOW);

        assertEquals(RiskDecisionStatus.PASS, pass.getStatus());
        assertEquals(RiskDecisionStatus.REJECT, reject.getStatus());
        assertEquals("RISK_REJECTED", reject.getReasonCode());
    }
}
