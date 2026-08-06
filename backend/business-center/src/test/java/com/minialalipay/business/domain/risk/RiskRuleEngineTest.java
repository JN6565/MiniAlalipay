package com.minialalipay.business.domain.risk;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 受理前风控规则引擎测试：覆盖限额拒绝、高频转人工与高风险提示的优先级。 */
class RiskRuleEngineTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    private static RiskAssessment assess(long amountFen, int recent, int repeated, boolean traded) {
        return RiskRuleEngine.assess(new RiskContext("QR_PAY_ORDER", "order-1", "payer-1", "account-payee-1",
                amountFen, recent, repeated, traded, NOW));
    }

    @Test
    void 小额无历史默认放行且风险等级为低() {
        RiskAssessment result = assess(100, 0, 0, false);

        assertEquals(RiskDecisionStatus.PASS, result.status());
        assertEquals("LOW", result.riskLevel());
        assertNull(result.reasonCode());
    }

    @Test
    void 单笔金额超五万元直接拒绝() {
        RiskAssessment result = assess(5_000_001L, 0, 0, false);

        assertEquals(RiskDecisionStatus.REJECT, result.status());
        assertEquals("R-02_PAYMENT_AMOUNT_EXCEEDS_LIMIT", result.reasonCode());
        assertEquals("HIGH", result.riskLevel());
    }

    @Test
    void 高频窗口内超过五笔转人工复核() {
        RiskAssessment result = assess(100, 6, 0, false);

        assertEquals(RiskDecisionStatus.MANUAL_REVIEW, result.status());
        assertEquals("R-03_HIGH_FREQUENCY_TRADING", result.reasonCode());
    }

    @Test
    void 大额交易标记高风险但放行() {
        RiskAssessment result = assess(500_000L, 0, 0, false);

        assertEquals(RiskDecisionStatus.PASS, result.status());
        assertEquals("HIGH", result.riskLevel());
        assertEquals("R-04_LARGE_AMOUNT", result.reasonCode());
    }

    @Test
    void 重复特征标记高风险但放行() {
        RiskAssessment result = assess(100, 0, 1, true);

        assertEquals(RiskDecisionStatus.PASS, result.status());
        assertEquals("R-06_REPEATED_PAYMENT_FEATURE", result.reasonCode());
    }

    @Test
    void 新交易对手且金额达标标记高风险() {
        RiskAssessment result = assess(100_000L, 0, 0, false);

        assertEquals(RiskDecisionStatus.PASS, result.status());
        assertEquals("R-05_NEW_PAYEE", result.reasonCode());
    }

    @Test
    void 与历史对手的小额交易不触发新对手提示() {
        RiskAssessment result = assess(100, 0, 0, true);

        assertEquals(RiskDecisionStatus.PASS, result.status());
        assertEquals("LOW", result.riskLevel());
    }

    @Test
    void 高频转人工优先于大额提示() {
        RiskAssessment result = assess(500_000L, 6, 0, false);

        assertEquals(RiskDecisionStatus.MANUAL_REVIEW, result.status());
        assertEquals("R-03_HIGH_FREQUENCY_TRADING", result.reasonCode());
    }

    @Test
    void 单笔限额拒绝优先于高频与提示() {
        RiskAssessment result = assess(5_000_001L, 6, 1, false);

        assertEquals(RiskDecisionStatus.REJECT, result.status());
        assertEquals("R-02_PAYMENT_AMOUNT_EXCEEDS_LIMIT", result.reasonCode());
    }
}
