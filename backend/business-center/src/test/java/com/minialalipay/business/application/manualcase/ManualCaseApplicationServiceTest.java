package com.minialalipay.business.application.manualcase;

import com.minialalipay.business.application.port.ManualCaseStore;
import com.minialalipay.business.application.port.RiskReviewResumePort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.manualcase.ManualCase;
import com.minialalipay.business.domain.manualcase.ManualCaseStatus;
import com.minialalipay.business.domain.manualcase.ManualCaseType;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManualCaseApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    void 领取与解决工单写入操作者理由证据并使用版本CAS() {
        MemoryStore store = new MemoryStore(ManualCase.open("case-1", ManualCaseType.RISK_PRECHECK,
                "QR_PAY_ORDER", "order-1", "RISK_MANUAL_REVIEW", NOW));
        ManualCaseApplicationService service = service(store);

        ManualCase claimed = service.decide("operator-1", "case-1", ManualCaseApplicationService.Decision.CLAIM,
                0L, null, null, KEY);
        ManualCase resolved = service.decide("operator-1", "case-1", ManualCaseApplicationService.Decision.RESOLVE,
                1L, "核验通过", "evidence-1", "123e4567-e89b-12d3-a456-426614174001");

        assertEquals("operator-1", claimed.getOperatorId());
        assertEquals("核验通过", resolved.getLastReason());
        assertEquals("evidence-1", resolved.getEvidenceReference());
        assertThrows(BusinessException.class, () -> service.decide("operator-1", "case-1",
                ManualCaseApplicationService.Decision.CLOSE, 1L, "关闭", "evidence-2",
                "123e4567-e89b-12d3-a456-426614174002"));
    }

    @Test
    void 缺少证据时拒绝处置() {
        MemoryStore store = new MemoryStore(ManualCase.open("case-1", ManualCaseType.RISK_PRECHECK,
                "QR_PAY_ORDER", "order-1", "RISK_MANUAL_REVIEW", NOW));
        ManualCaseApplicationService service = service(store);
        service.decide("operator-1", "case-1", ManualCaseApplicationService.Decision.CLAIM, 0L, null, null, KEY);

        assertThrows(BusinessException.class, () -> service.decide("operator-1", "case-1",
                ManualCaseApplicationService.Decision.RESOLVE, 1L, "核验通过", null,
                "123e4567-e89b-12d3-a456-426614174001"));
    }

    @Test
    void 同键同参回放快照且同键异参拒绝() {
        MemoryStore store = new MemoryStore(ManualCase.open("case-1", ManualCaseType.RISK_PRECHECK,
                "QR_PAY_ORDER", "order-1", "RISK_MANUAL_REVIEW", NOW));
        ManualCaseApplicationService service = service(store);

        ManualCase first = service.decide("operator-1", "case-1", ManualCaseApplicationService.Decision.CLAIM,
                0L, null, null, KEY);
        ManualCase replayed = service.decide("operator-1", "case-1", ManualCaseApplicationService.Decision.CLAIM,
                0L, null, null, KEY);

        assertEquals(first.getVersion(), replayed.getVersion());
        assertEquals(1, store.updateCalls);
        assertThrows(BusinessException.class, () -> service.decide("operator-1", "case-1",
                ManualCaseApplicationService.Decision.CLAIM, 1L, null, null, KEY));
    }

    @Test
    void 解决风控预检工单后恢复来源订单到待确认() {
        MemoryStore store = new MemoryStore(ManualCase.open("case-1", ManualCaseType.RISK_PRECHECK,
                "QR_PAY_ORDER", "order-1", "RISK_MANUAL_REVIEW", NOW));
        RecordingResumePort resumePort = new RecordingResumePort();
        ManualCaseApplicationService service = resumeService(store, resumePort);

        service.decide("operator-1", "case-1", ManualCaseApplicationService.Decision.CLAIM, 0L, null, null, KEY);
        service.decide("operator-1", "case-1", ManualCaseApplicationService.Decision.RESOLVE, 1L, "核验通过",
                "evidence-1", "123e4567-e89b-12d3-a456-426614174001");

        assertEquals("QR_PAY_ORDER", resumePort.subjectType);
        assertEquals("order-1", resumePort.subjectId);
    }

    private static ManualCaseApplicationService service(MemoryStore store) {
        return new ManualCaseApplicationService(store, secure(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ManualCaseApplicationService resumeService(MemoryStore store, RiskReviewResumePort resumePort) {
        return new ManualCaseApplicationService(store, secure(), resumePort, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static SecurityMaterialPort secure() {
        return new SecurityMaterialPort() {
            private int id;
            @Override public String newId() { return "record-" + ++id; }
            @Override public String newTraceId() { return "0".repeat(32); }
            @Override public String newConfirmationToken() { return "unused"; }
            @Override public String newQrToken() { return "unused-qr"; }
            @Override public String newCollectionToken() { return "unused-collection"; }
            @Override public byte[] digest(String value) { return value.getBytes(StandardCharsets.UTF_8); }
            @Override public String stableId(String value) { return value; }
            @Override public long stablePositiveLong(String value) { return 1L; }
        };
    }

    private static final class MemoryStore implements ManualCaseStore {
        private ManualCase value;
        private final Map<String, DecisionIdempotencyRecord> idempotency = new HashMap<>();
        private int updateCalls;
        private MemoryStore(ManualCase value) { this.value = value; }
        @Override public List<ManualCase> list(String cursor, ManualCaseStatus status, ManualCaseType type, int limit) {
            return List.of(value);
        }
        @Override public Optional<ManualCase> find(String caseId) { return Optional.ofNullable(value); }
        @Override public boolean create(ManualCase manualCase) { value = manualCase; return true; }
        @Override public boolean update(ManualCase manualCase, long expectedVersion) { updateCalls++; value = manualCase; return true; }
        @Override public Optional<DecisionIdempotencyRecord> findDecisionIdempotency(String operatorId, String key) {
            return Optional.ofNullable(idempotency.get(operatorId + ':' + key));
        }
        @Override public boolean reserveDecisionIdempotency(String recordId, String operatorId, String key, byte[] hash) {
            return idempotency.putIfAbsent(operatorId + ':' + key, new DecisionIdempotencyRecord(hash, null)) == null;
        }
        @Override public void completeDecisionIdempotency(String operatorId, String key, ManualCase manualCase) {
            DecisionIdempotencyRecord record = idempotency.get(operatorId + ':' + key);
            idempotency.put(operatorId + ':' + key, new DecisionIdempotencyRecord(record.requestHash(), manualCase));
        }
    }

    private static final class RecordingResumePort implements RiskReviewResumePort {
        private String subjectType;
        private String subjectId;
        @Override public boolean resumeToConfirmation(String subjectType, String subjectId, Instant now) {
            this.subjectType = subjectType;
            this.subjectId = subjectId;
            return true;
        }
    }
}
