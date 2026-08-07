package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.monitoring.Alert;
import com.minialalipay.business.domain.monitoring.AlertRule;
import com.minialalipay.business.domain.monitoring.AlertStatus;
import com.minialalipay.business.domain.monitoring.DataQualityResult;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import com.minialalipay.business.domain.monitoring.MetricDefinition;
import com.minialalipay.business.domain.monitoring.RealtimeMetric;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 监控应用服务测试：告警处置幂等、证据校验、错误码映射和报表门禁。 */
class MonitoringApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final String KEY = "00000000-0000-0000-0000-000000000001";

    private FakeStore store;
    private MonitoringApplicationService service;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        service = new MonitoringApplicationService(store, new TestSecurity(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void 确认告警成功且同键同参重放返回同一结果() {
        store.alerts.put("alert-1", Alert.open("alert-1", "TCC_TIMEOUT", "CRITICAL", NOW));

        Alert first = service.acknowledgeAlert("ops-1", "alert-1", 0L, "开始处置", KEY);
        Alert replay = service.acknowledgeAlert("ops-1", "alert-1", 0L, "开始处置", KEY);

        assertEquals(AlertStatus.ACKNOWLEDGED, first.getStatus());
        assertEquals(first.getVersion(), replay.getVersion());
        assertEquals(1L, replay.getVersion());
    }

    @Test
    void 同幂等键用于不同请求被拒绝() {
        store.alerts.put("alert-1", Alert.open("alert-1", "TCC_TIMEOUT", "CRITICAL", NOW));
        service.acknowledgeAlert("ops-1", "alert-1", 0L, "开始处置", KEY);

        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.acknowledgeAlert("ops-1", "alert-1", 0L, "另一个理由", KEY));
        assertEquals(BusinessErrorCode.IDEMPOTENCY_CONFLICT.code(), conflict.errorCode().code());
    }

    @Test
    void 恢复和关闭缺少证据被拒绝() {
        store.alerts.put("alert-1", Alert.open("alert-1", "TCC_TIMEOUT", "CRITICAL", NOW));
        service.acknowledgeAlert("ops-1", "alert-1", 0L, "开始处置", KEY);

        BusinessException missingEvidence = assertThrows(BusinessException.class,
                () -> service.resolveAlert("ops-1", "alert-1", 1L, "已恢复", "  ", KEY));
        assertEquals(BusinessErrorCode.EVIDENCE_REQUIRED.code(), missingEvidence.errorCode().code());
    }

    @Test
    void 告警不存在返回告警不存在错误码() {
        BusinessException notFound = assertThrows(BusinessException.class,
                () -> service.acknowledgeAlert("ops-1", "alert-missing", 0L, "开始处置", KEY));
        assertEquals(BusinessErrorCode.ALERT_NOT_FOUND.code(), notFound.errorCode().code());
    }

    @Test
    void 版本不匹配映射为版本冲突状态不合法映射为告警状态错误() {
        store.alerts.put("alert-1", Alert.open("alert-1", "TCC_TIMEOUT", "CRITICAL", NOW));

        BusinessException versionConflict = assertThrows(BusinessException.class,
                () -> service.acknowledgeAlert("ops-1", "alert-1", 9L, "开始处置", KEY));
        assertEquals(BusinessErrorCode.VERSION_CONFLICT.code(), versionConflict.errorCode().code());

        BusinessException stateInvalid = assertThrows(BusinessException.class,
                () -> service.closeAlert("ops-1", "alert-1", 0L, "直接关闭", "证据",
                        "00000000-0000-0000-0000-000000000099"));
        assertEquals(BusinessErrorCode.ALERT_STATE_INVALID.code(), stateInvalid.errorCode().code());
    }

    @Test
    void 报表未通过质量门禁时返回未发布错误() {
        BusinessException notPublished = assertThrows(BusinessException.class,
                () -> service.listDailyReports(LocalDate.of(2026, 8, 4)));
        assertEquals(BusinessErrorCode.REPORT_NOT_PUBLISHED.code(), notPublished.errorCode().code());
    }

    @Test
    void 实时指标时间范围不合法被拒绝() {
        BusinessException invalidRange = assertThrows(BusinessException.class,
                () -> service.listRealtimeMetrics(null, NOW.plusSeconds(60), NOW));
        assertEquals(BusinessErrorCode.INVALID_TIME_RANGE.code(), invalidRange.errorCode().code());
    }

    private static final class FakeStore implements MonitoringProjectionStore {
        private final Map<String, Alert> alerts = new LinkedHashMap<>();
        private final Map<String, AlertOpsIdempotencyRecord> idempotency = new LinkedHashMap<>();

        @Override public List<Alert> listAlerts(String status, String severity, String cursor, int limit) {
            List<Alert> all = new ArrayList<>(alerts.values());
            if (status != null && !status.isBlank()) {
                all.removeIf(alert -> !status.equals(alert.getStatus().name()));
            }
            if (severity != null && !severity.isBlank()) {
                all.removeIf(alert -> !severity.equals(alert.getSeverity()));
            }
            return all;
        }
        @Override public Optional<Alert> findAlert(String alertId) {
            // 模拟数据库快照：返回拷贝，避免服务层原地变更污染持久化版本比较。
            return Optional.ofNullable(alerts.get(alertId)).map(FakeStore::copy);
        }
        @Override public boolean updateAlert(Alert alert, long expectedVersion) {
            Alert current = alerts.get(alert.getAlertId());
            if (current == null || current.getVersion() != expectedVersion) return false;
            alerts.put(alert.getAlertId(), alert);
            return true;
        }
        private static Alert copy(Alert alert) {
            return new Alert(alert.getAlertId(), alert.getAlertType(), alert.getSeverity(), alert.getStatus(),
                    alert.getOperatorId(), alert.getLastReason(), alert.getVersion(),
                    alert.getCreatedAt(), alert.getUpdatedAt());
        }
        @Override public List<RealtimeMetric> listRealtimeMetrics(String metricCode, Instant from, Instant to) { return List.of(); }
        @Override public List<DailyMetric> listDailyReports(LocalDate reportDate) { return List.of(); }
        @Override public List<DataQualityResult> listDataQuality(LocalDate dataDate, String jobCode, String ruleCode) { return List.of(); }
        @Override public List<MetricDefinition> listMetricDefinitions() { return List.of(); }
        @Override public List<AlertRule> listAlertRules() { return List.of(); }
        @Override public Optional<AlertRule> findAlertRule(String ruleCode) { return Optional.empty(); }
        @Override public boolean updateAlertRuleThreshold(AlertRule rule, long expectedVersion) { return false; }
        @Override public Optional<AlertOpsIdempotencyRecord> findAlertOpsIdempotency(String operatorId, String idempotencyKey) {
            return Optional.ofNullable(idempotency.get(key(operatorId, idempotencyKey)));
        }
        @Override public boolean reserveAlertOpsIdempotency(String recordId, String operatorId, String idempotencyKey, byte[] requestHash) {
            return idempotency.putIfAbsent(key(operatorId, idempotencyKey), new AlertOpsIdempotencyRecord(requestHash, null)) == null;
        }
        @Override public void completeAlertOpsIdempotency(String operatorId, String idempotencyKey, Alert alert) {
            AlertOpsIdempotencyRecord existing = idempotency.get(key(operatorId, idempotencyKey));
            idempotency.put(key(operatorId, idempotencyKey), new AlertOpsIdempotencyRecord(existing.requestHash(), alert));
        }
        private static String key(String operatorId, String idempotencyKey) { return operatorId + ":" + idempotencyKey; }
    }

    private static final class TestSecurity implements SecurityMaterialPort {
        private int id;
        @Override public String newId() { return "record-" + ++id; }
        @Override public String newTraceId() { return "0".repeat(32); }
        @Override public String newConfirmationToken() { return "unused"; }
        @Override public String newQrToken() { return "unused"; }
        @Override public String newCollectionToken() { return "unused"; }
        @Override public byte[] digest(String value) {
            try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        @Override public String stableId(String value) { return value; }
        @Override public long stablePositiveLong(String value) { return 1L; }
    }
}
