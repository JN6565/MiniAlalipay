package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.monitoring.Alert;
import com.minialalipay.business.domain.monitoring.AlertRule;
import com.minialalipay.business.domain.monitoring.DataQualityResult;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import com.minialalipay.business.domain.monitoring.MetricDefinition;
import com.minialalipay.business.domain.monitoring.RealtimeMetric;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AlertThresholdEvaluatorTest {
    private static final Instant NOW = Instant.parse("2026-08-08T07:00:00Z");

    @Test
    void 指标超过阈值且没有活动告警时生成告警事件() {
        RecordingProjectionStore projection = new RecordingProjectionStore();
        projection.rules = List.of(new AlertRule("DUPLICATE_CHARGE", "重复扣款", "duplicate_charge_count",
                "CRITICAL", "GT", 0, true, 0, "seed", NOW));
        projection.count = 1;
        RecordingEventStore events = new RecordingEventStore();

        new AlertThresholdEvaluator(projection, new MonitoringEventConsumer("ops-projection", events),
                new FakeSecurity(), Clock.fixed(NOW, ZoneOffset.UTC), 60).evaluateRules();

        assertEquals(1, events.projected.size());
        assertEquals("alert.status.changed", events.projected.get(0).eventType());
        assertEquals("OPEN", events.projected.get(0).attributes().get("status"));
    }

    @Test
    void 已有活动告警时不重复生成() {
        RecordingProjectionStore projection = new RecordingProjectionStore();
        projection.rules = List.of(new AlertRule("DUPLICATE_CHARGE", "重复扣款", "duplicate_charge_count",
                "CRITICAL", "GT", 0, true, 0, "seed", NOW));
        projection.count = 2;
        projection.active = Optional.of(Alert.open("01J00000000000000000000006", "DUPLICATE_CHARGE", "CRITICAL", NOW));
        RecordingEventStore events = new RecordingEventStore();

        new AlertThresholdEvaluator(projection, new MonitoringEventConsumer("ops-projection", events),
                new FakeSecurity(), Clock.fixed(NOW, ZoneOffset.UTC), 60).evaluateRules();

        assertEquals(0, events.projected.size());
    }

    private static final class RecordingEventStore implements MonitoringEventStore {
        private final List<MonitoringEvent> projected = new ArrayList<>();
        @Override public InboxClaimResult claim(String consumerName, String eventId) { return InboxClaimResult.CLAIMED; }
        @Override public void project(MonitoringEvent event) { projected.add(event); }
        @Override public void complete(String consumerName, String eventId) { }
        @Override public void fail(String consumerName, String eventId, String reason) { }
        @Override public void quarantine(MonitoringEvent event, String reason) { }
    }

    private static final class RecordingProjectionStore implements MonitoringProjectionStore {
        private List<AlertRule> rules = List.of();
        private long count;
        private Optional<Alert> active = Optional.empty();
        @Override public List<AlertRule> listAlertRules() { return rules; }
        @Override public long countMetric(String metricCode, Instant from, Instant to) { return count; }
        @Override public Optional<Alert> findActiveAlertByRule(String ruleCode) { return active; }
        @Override public List<Alert> listAlerts(String status, String severity, String cursor, int limit) { return List.of(); }
        @Override public Optional<Alert> findAlert(String alertId) { return Optional.empty(); }
        @Override public boolean updateAlert(Alert alert, long expectedVersion) { return false; }
        @Override public List<RealtimeMetric> listRealtimeMetrics(String metricCode, Instant from, Instant to) { return List.of(); }
        @Override public List<DailyMetric> listDailyReports(LocalDate reportDate) { return List.of(); }
        @Override public List<DataQualityResult> listDataQuality(LocalDate dataDate, String jobCode, String ruleCode) { return List.of(); }
        @Override public List<MetricDefinition> listMetricDefinitions() { return List.of(); }
        @Override public Optional<AlertRule> findAlertRule(String ruleCode) { return Optional.empty(); }
        @Override public boolean updateAlertRuleThreshold(AlertRule rule, long expectedVersion) { return false; }
        @Override public Optional<AlertOpsIdempotencyRecord> findAlertOpsIdempotency(String operatorId, String idempotencyKey) { return Optional.empty(); }
        @Override public boolean reserveAlertOpsIdempotency(String recordId, String operatorId, String idempotencyKey, byte[] requestHash) { return false; }
        @Override public void completeAlertOpsIdempotency(String operatorId, String idempotencyKey, Alert alert) { }
    }

    private static final class FakeSecurity implements SecurityMaterialPort {
        @Override public String newId() { return "01J00000000000000000000007"; }
        @Override public String newTraceId() { return "trace-0000000000000000000000000000"; }
        @Override public String newConfirmationToken() { return "unused"; }
        @Override public String newQrToken() { return "unused"; }
        @Override public String newCollectionToken() { return "unused"; }
        @Override public byte[] digest(String value) { return value.getBytes(); }
        @Override public String stableId(String value) { return "01J00000000000000000000008"; }
        @Override public long stablePositiveLong(String value) { return 1; }
    }
}
