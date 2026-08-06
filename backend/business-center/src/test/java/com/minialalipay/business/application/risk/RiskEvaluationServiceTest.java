package com.minialalipay.business.application.risk;

import com.minialalipay.business.application.port.RiskDecisionStore;
import com.minialalipay.business.application.port.RiskHistoryPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.risk.RiskDecision;
import com.minialalipay.business.domain.risk.RiskDecisionStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 风控预检服务测试：规则评估联动、决策落库与拒绝/人工复核裁决。 */
class RiskEvaluationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    @Test
    void 小额无历史时放行并保存放行决策() {
        MemoryRiskStore store = new MemoryRiskStore();
        RiskEvaluationService service = service(store, new MemoryRiskHistory(0, 0, false));

        RiskEvaluationService.RiskVerdict verdict = service.evaluatePrecheck(
                "QR_PAY_ORDER", "order-1", "payer-1", "account-payee-1", 100, NOW);

        assertEquals(RiskDecisionStatus.PASS, verdict.status());
        assertEquals("LOW", verdict.riskLevel());
        assertEquals(1, store.saved.size());
        assertEquals(RiskDecisionStatus.PASS, store.saved.get(0).getStatus());
    }

    @Test
    void 单笔金额超限返回拒绝并落库拒绝决策() {
        MemoryRiskStore store = new MemoryRiskStore();
        RiskEvaluationService service = service(store, new MemoryRiskHistory(0, 0, false));

        RiskEvaluationService.RiskVerdict verdict = service.evaluatePrecheck(
                "QR_PAY_ORDER", "order-1", "payer-1", "account-payee-1", 5_000_001L, NOW);

        assertEquals(RiskDecisionStatus.REJECT, verdict.status());
        assertEquals("R-02_PAYMENT_AMOUNT_EXCEEDS_LIMIT", verdict.reasonCode());
        assertEquals(RiskDecisionStatus.REJECT, store.saved.get(0).getStatus());
    }

    @Test
    void 高频历史返回人工复核并落库复核决策() {
        MemoryRiskStore store = new MemoryRiskStore();
        RiskEvaluationService service = service(store, new MemoryRiskHistory(6, 0, false));

        RiskEvaluationService.RiskVerdict verdict = service.evaluatePrecheck(
                "QR_PAY_ORDER", "order-1", "payer-1", "account-payee-1", 100, NOW);

        assertEquals(RiskDecisionStatus.MANUAL_REVIEW, verdict.status());
        assertEquals("R-03_HIGH_FREQUENCY_TRADING", verdict.reasonCode());
        assertEquals(RiskDecisionStatus.MANUAL_REVIEW, store.saved.get(0).getStatus());
    }

    private static RiskEvaluationService service(MemoryRiskStore store, RiskHistoryPort history) {
        return new RiskEvaluationService(store, history, new TestSecurity());
    }

    private static final class MemoryRiskStore implements RiskDecisionStore {
        private final java.util.List<RiskDecision> saved = new java.util.ArrayList<>();
        @Override public Optional<RiskDecision> findLatestBySubject(String subjectType, String subjectId) {
            return saved.stream().reduce((first, second) -> second);
        }
        @Override public boolean save(RiskDecision decision) { saved.add(decision); return true; }
    }

    private record MemoryRiskHistory(int recent, int repeated, boolean traded) implements RiskHistoryPort {
        @Override public int countRecentPayments(String payerUserId, Instant since) { return recent; }
        @Override public int countRepeatedPayments(String payerUserId, String payeeAccountId, long amountFen, Instant since) { return repeated; }
        @Override public boolean hasTradedWith(String payerUserId, String payeeAccountId) { return traded; }
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int sequence;
        @Override public String newId() { return "id-" + ++sequence; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "unused"; }
        @Override public String newQrToken() { return "unused-qr"; }
        @Override public String newCollectionToken() { return "unused-collection"; }
        @Override public byte[] digest(String value) { return value.getBytes(StandardCharsets.UTF_8); }
        @Override public String stableId(String value) { return value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
