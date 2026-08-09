package com.minialalipay.business.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.application.port.DailyReportStore;
import com.minialalipay.business.domain.monitoring.Alert;
import com.minialalipay.business.domain.monitoring.AlertRule;
import com.minialalipay.business.domain.monitoring.AlertStatus;
import com.minialalipay.business.domain.monitoring.DataQualityResult;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import com.minialalipay.business.domain.monitoring.MetricDefinition;
import com.minialalipay.business.domain.monitoring.RealtimeMetric;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * metrics_db 监控投影的只读查询与告警处置仓储实现。
 *
 * <p>只读取或更新 metrics_db 投影，不修改业务资金事实。实时指标按分析事件的时间桶聚合为分钟级只读投影。</p>
 */
@Repository
public class JdbcMonitoringProjectionStore implements MonitoringProjectionStore, DailyReportStore {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** 创建监控投影仓储。 */
    @Autowired
    public JdbcMonitoringProjectionStore(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    JdbcMonitoringProjectionStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public List<Alert> listAlerts(String status, String severity, String cursor, int limit) {
        String afterId = cursor == null || cursor.isBlank() ? "" : cursor;
        StringBuilder sql = new StringBuilder("SELECT alert_id,rule_code,severity,status,assignee_id,last_reason,version,"
                + "opened_at,updated_at FROM metrics_db.monitor_alert WHERE ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        if (status != null && !status.isBlank()) {
            sql.append("status=?");
            args.add(status);
            first = false;
        }
        if (severity != null && !severity.isBlank()) {
            if (!first) sql.append(" AND ");
            sql.append("severity=?");
            args.add(severity);
            first = false;
        }
        sql.append(first ? "alert_id>?" : " AND alert_id>?").append(" ORDER BY alert_id ASC LIMIT ?");
        args.add(afterId);
        args.add(limit);
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapAlert(rs), args.toArray());
    }

    @Override
    public Optional<Alert> findAlert(String alertId) {
        return jdbc.query("SELECT alert_id,rule_code,severity,status,assignee_id,last_reason,version,opened_at,updated_at "
                + "FROM metrics_db.monitor_alert WHERE alert_id=?",
                rs -> rs.next() ? Optional.of(mapAlert(rs)) : Optional.empty(), alertId);
    }

    @Override
    public boolean updateAlert(Alert alert, long expectedVersion) {
        Instant closedAt = alert.getStatus() == AlertStatus.CLOSED ? alert.getUpdatedAt() : null;
        return jdbc.update("UPDATE metrics_db.monitor_alert SET status=?,assignee_id=?,last_reason=?,version=?,updated_at=?,closed_at=? "
                        + "WHERE alert_id=? AND version=?",
                alert.getStatus().name(), alert.getOperatorId(), alert.getLastReason(), alert.getVersion(),
                Timestamp.from(alert.getUpdatedAt()), closedAt == null ? null : Timestamp.from(closedAt),
                alert.getAlertId(), expectedVersion) == 1;
    }

    @Override
    public List<RealtimeMetric> listRealtimeMetrics(String metricCode, Instant from, Instant to) {
        List<AnalyticsRow> rows = new ArrayList<>();
        if (metricCode == null || metricCode.isBlank()) {
            jdbc.query("SELECT event_type,occurred_at FROM metrics_db.analytics_event "
                    + "WHERE occurred_at>=? AND occurred_at<? ORDER BY occurred_at ASC",
                    (rs, rowNum) -> rows.add(new AnalyticsRow(rs.getString("event_type"), rs.getTimestamp("occurred_at").toInstant())),
                    Timestamp.from(from), Timestamp.from(to));
        } else {
            jdbc.query("SELECT event_type,occurred_at FROM metrics_db.analytics_event "
                    + "WHERE event_type=? AND occurred_at>=? AND occurred_at<? ORDER BY occurred_at ASC",
                    (rs, rowNum) -> rows.add(new AnalyticsRow(rs.getString("event_type"), rs.getTimestamp("occurred_at").toInstant())),
                    metricCode, Timestamp.from(from), Timestamp.from(to));
        }
        return bucketToMinuteMetrics(rows);
    }

    @Override
    public long countMetric(String metricCode, Instant from, Instant to) {
        String condition = switch (metricCode) {
            case "duplicate_charge_count" -> "event_type='duplicate_charge.detected'";
            case "ledger_imbalance_count" -> "event_type='reconciliation.diff.detected' "
                    + "AND JSON_UNQUOTE(JSON_EXTRACT(metrics_json,'$.diffType')) IN ('LEDGER_IMBALANCE','DEBIT_CREDIT_IMBALANCE')";
            case "saga_compensate_fail_count" -> "event_type='transaction.status.changed' "
                    + "AND JSON_UNQUOTE(JSON_EXTRACT(metrics_json,'$.status')) IN ('COMPENSATE_FAILED','SAGA_COMPENSATE_FAILED')";
            default -> "event_type=?";
        };
        if (condition.equals("event_type=?")) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM metrics_db.analytics_event WHERE " + condition
                            + " AND occurred_at>=? AND occurred_at<?", Long.class, metricCode,
                    Timestamp.from(from), Timestamp.from(to));
        }
        return jdbc.queryForObject("SELECT COUNT(*) FROM metrics_db.analytics_event WHERE " + condition
                        + " AND occurred_at>=? AND occurred_at<?", Long.class,
                Timestamp.from(from), Timestamp.from(to));
    }

    @Override
    public long countOpenAlerts() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM metrics_db.monitor_alert WHERE status='OPEN'", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<Alert> findActiveAlertByRule(String ruleCode) {
        return jdbc.query("SELECT alert_id,rule_code,severity,status,assignee_id,last_reason,version,opened_at,updated_at "
                        + "FROM metrics_db.monitor_alert WHERE rule_code=? AND status IN ('OPEN','ACKNOWLEDGED') "
                        + "ORDER BY opened_at DESC LIMIT 1",
                rs -> rs.next() ? Optional.of(mapAlert(rs)) : Optional.empty(), ruleCode);
    }

    @Override
    public List<DailyMetricValue> aggregate(LocalDate reportDate, String zoneId) {
        ZoneId zone = ZoneId.of(zoneId);
        return aggregate(reportDate.atStartOfDay(zone).toInstant(), reportDate.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @Override
    public List<DailyMetricValue> aggregate(Instant from, Instant to) {
        // T+1 报表按事件类型聚合为单指标行；当前口径不按业务类型拆分维度，避免同一指标码多行造成页面重复。
        return jdbc.query("SELECT event_type,COUNT(*) FROM metrics_db.analytics_event "
                        + "WHERE occurred_at>=? AND occurred_at<? GROUP BY event_type ORDER BY event_type",
                (rs, rowNum) -> dailyMetricValue(rs.getString(1), rs.getLong(2)),
                Timestamp.from(from), Timestamp.from(to));
    }

    @Override
    public long countIncompleteInbox(LocalDate reportDate, String zoneId) {
        ZoneId zone = ZoneId.of(zoneId);
        return countIncompleteInbox(reportDate.atStartOfDay(zone).toInstant(), reportDate.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @Override
    public long countIncompleteInbox(Instant from, Instant to) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM metrics_db.inbox_event WHERE status<>'DONE' AND received_at>=? AND received_at<?",
                Long.class, Timestamp.from(from), Timestamp.from(to));
        return count == null ? 0L : count;
    }

    @Override
    public List<DailyReportStore.InboxFailure> listIncompleteInboxFailures(Instant from, Instant to) {
        return jdbc.query("SELECT event_id,COALESCE(failure_reason,'未记录失败原因'),retry_count,status "
                        + "FROM metrics_db.inbox_event WHERE status<>'DONE' AND received_at>=? AND received_at<? "
                        + "ORDER BY received_at ASC LIMIT 1000",
                (rs, rowNum) -> new DailyReportStore.InboxFailure(rs.getString("event_id"),
                        rs.getString(2), rs.getInt("retry_count"), rs.getString("status")),
                Timestamp.from(from), Timestamp.from(to));
    }

    @Override
    public long countQuarantined(LocalDate reportDate, String zoneId) {
        ZoneId zone = ZoneId.of(zoneId);
        return countQuarantined(reportDate.atStartOfDay(zone).toInstant(), reportDate.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @Override
    public long countQuarantined(Instant from, Instant to) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM metrics_db.quarantined_event WHERE quarantined_at>=? AND quarantined_at<?",
                Long.class, Timestamp.from(from), Timestamp.from(to));
        return count == null ? 0L : count;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void publish(LocalDate reportDate, List<DailyMetricValue> metrics, List<QualityCheck> checks, Instant now) {
        boolean passed = checks.stream().allMatch(check -> "PASSED".equals(check.status()));
        for (QualityCheck check : checks) {
            String resultId = stableId("TPLUS1:" + reportDate + ":" + check.ruleCode());
            jdbc.update("INSERT INTO metrics_db.quality_result "
                            + "(result_id,task_code,data_date,rule_code,status,expected_value,actual_value,evidence_json,checked_at) "
                            + "VALUES (?,'TPLUS1',?,?,?,?,?,?,?) "
                            + "ON DUPLICATE KEY UPDATE status=VALUES(status),actual_value=VALUES(actual_value),"
                            + "evidence_json=VALUES(evidence_json),checked_at=VALUES(checked_at)",
                    resultId, java.sql.Date.valueOf(reportDate), check.ruleCode(), check.status(), 0L,
                    check.failedCount(), check.evidenceJson(), Timestamp.from(now));
        }
        // 失败重跑必须隐藏同日旧版本，防止页面继续展示已经失去质量门禁的数值。
        jdbc.update("UPDATE metrics_db.daily_metric SET quality_status='FAILED' WHERE metric_date=?",
                java.sql.Date.valueOf(reportDate));
        if (!passed) return;
        for (DailyMetricValue metric : metrics) {
            jdbc.update("INSERT INTO metrics_db.daily_metric "
                            + "(metric_date,metric_code,dimension_hash,dimensions_json,value_decimal,quality_status,version) "
                            + "VALUES (?,?,?,?,?,'PASSED',?) "
                            + "ON DUPLICATE KEY UPDATE dimensions_json=VALUES(dimensions_json),value_decimal=VALUES(value_decimal),"
                            + "quality_status='PASSED'",
                    java.sql.Date.valueOf(reportDate), metric.metricCode(), metric.dimensionHash(), metric.dimensionsJson(),
                    metric.value(), metric.version());
        }
    }

    private static DailyMetricValue dailyMetricValue(String eventType, long value) {
        // 当前指标口径无维度，统一写入空维度 JSON，保证同一指标码单日仅一条聚合行。
        return new DailyMetricValue(eventType, digest("{}"), "{}", value, 1);
    }

    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("运行环境缺少 SHA-256", impossible); }
    }

    private static String stableId(String value) {
        return java.util.HexFormat.of().formatHex(digest(value)).substring(0, 26).toUpperCase();
    }

    private static List<RealtimeMetric> bucketToMinuteMetrics(List<AnalyticsRow> rows) {
        Map<MinuteBucket, Long> counts = new LinkedHashMap<>();
        for (AnalyticsRow row : rows) {
            Instant bucket = row.occurredAt().minusSeconds(row.occurredAt().getEpochSecond() % 60);
            counts.merge(new MinuteBucket(row.eventType(), bucket), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int byTime = right.getKey().bucketAt().compareTo(left.getKey().bucketAt());
                    return byTime != 0 ? byTime : left.getKey().metricCode().compareTo(right.getKey().metricCode());
                })
                .map(entry -> new RealtimeMetric(entry.getKey().metricCode(), entry.getKey().bucketAt(),
                        entry.getValue(), "v1", "PASSED"))
                .toList();
    }

    @Override
    public List<DailyMetric> listDailyReports(LocalDate reportDate) {
        String select = "SELECT metric_code,metric_date,value_decimal,version,quality_status FROM metrics_db.daily_metric ";
        if (reportDate == null) {
            // OpenAPI 中 reportDate 可选；未指定时返回最近一个已通过质量门禁的报表日期。
            return jdbc.query(select
                            + "WHERE metric_date=(SELECT MAX(metric_date) FROM metrics_db.daily_metric "
                            + "WHERE quality_status IN ('PASSED','WARNING')) "
                            + "AND quality_status IN ('PASSED','WARNING') ORDER BY metric_code ASC",
                    (rs, rowNum) -> mapDailyMetric(rs));
        }
        return jdbc.query(select + "WHERE metric_date=? AND quality_status IN ('PASSED','WARNING') ORDER BY metric_code ASC",
                (rs, rowNum) -> mapDailyMetric(rs), java.sql.Date.valueOf(reportDate));
    }

    @Override
    public DailyReportTransactionStats dailyReportTransactionStats(Instant from, Instant to,
                                                                    Instant previousFrom, Instant previousTo) {
        DailyReportTransactionStats current = queryDailyReportTransactionStats(from, to);
        DailyReportTransactionStats previous = queryDailyReportTransactionStats(previousFrom, previousTo);
        return new DailyReportTransactionStats(current.transactionCount(), current.transactionAmountFen(),
                current.successRateBps(), current.averageLatencyMs(), previous.transactionCount(),
                previous.transactionAmountFen(), previous.successRateBps(), previous.averageLatencyMs());
    }

    @Override
    public List<DailyReportTrendPoint> dailyReportTrend(Instant from, Instant to) {
        Map<Instant, DailyReportTrendAccumulator> buckets = new LinkedHashMap<>();
        jdbc.query("SELECT terminal_at,amount_fen FROM metrics_db.monitoring_transaction_final_projection "
                        + "WHERE status='SUCCESS' AND terminal_at>=? AND terminal_at<? ORDER BY terminal_at ASC",
                rs -> {
                    Instant bucket = rs.getTimestamp("terminal_at").toInstant().truncatedTo(ChronoUnit.HOURS);
                    buckets.computeIfAbsent(bucket, ignored -> new DailyReportTrendAccumulator()).add(rs.getLong("amount_fen"));
                }, Timestamp.from(from), Timestamp.from(to));
        return buckets.entrySet().stream()
                .map(entry -> new DailyReportTrendPoint(entry.getKey(), entry.getValue().transactionCount, entry.getValue().amountFen))
                .toList();
    }

    private DailyReportTransactionStats queryDailyReportTransactionStats(Instant from, Instant to) {
        return jdbc.query("SELECT COUNT(*) AS transaction_count,"
                        + "COALESCE(SUM(CASE WHEN status='SUCCESS' THEN amount_fen ELSE 0 END),0) AS amount_fen,"
                        + "COALESCE(SUM(CASE WHEN status='SUCCESS' THEN 1 ELSE 0 END),0) AS success_count "
                        + "FROM metrics_db.monitoring_transaction_final_projection "
                        + "WHERE terminal_at>=? AND terminal_at<? AND status IN ('SUCCESS','REVERSED','CANCELLED')",
                rs -> {
                    if (!rs.next()) return new DailyReportTransactionStats(0, 0, 0, 0, 0, 0, 0, 0);
                    long count = rs.getLong("transaction_count");
                    long success = rs.getLong("success_count");
                    return new DailyReportTransactionStats(count, rs.getLong("amount_fen"),
                            count == 0 ? 0 : success * 10_000L / count, 0L,
                            0, 0, 0, 0);
                }, Timestamp.from(from), Timestamp.from(to));
    }

    /** 单小时成功交易趋势累加器，金额始终保留为整数分。 */
    private static final class DailyReportTrendAccumulator {
        private long transactionCount;
        private long amountFen;

        private void add(long value) {
            transactionCount++;
            amountFen += value;
        }
    }

    @Override
    public Optional<DailyReportMetadata> findDailyReportMetadata(LocalDate reportDate) {
        return jdbc.query("SELECT MAX(q.checked_at) AS generated_at,COALESCE(MAX(d.version),1) AS report_version "
                        + "FROM metrics_db.quality_result q LEFT JOIN metrics_db.daily_metric d ON d.metric_date=q.data_date "
                        + "WHERE q.task_code='TPLUS1' AND q.data_date=?",
                rs -> {
                    if (!rs.next() || rs.getTimestamp("generated_at") == null) return Optional.empty();
                    return Optional.of(new DailyReportMetadata(rs.getTimestamp("generated_at").toInstant(),
                            "v" + rs.getInt("report_version")));
                }, java.sql.Date.valueOf(reportDate));
    }

    @Override
    public List<DailyReportReconciliation> listDailyReportReconciliation(Instant from, Instant to) {
        return jdbc.query("SELECT event_id,occurred_at,dimensions_json FROM metrics_db.analytics_event "
                        + "WHERE event_type='reconciliation.diff.detected' AND occurred_at>=? AND occurred_at<? "
                        + "ORDER BY occurred_at ASC LIMIT 500",
                (rs, rowNum) -> {
                    JsonNode dimensions = readJson(rs.getString("dimensions_json"));
                    return new DailyReportReconciliation(firstText(dimensions, "voucherNo", "voucherId"),
                            text(dimensions, "transactionId"), rs.getTimestamp("occurred_at").toInstant(),
                            firstLong(dimensions, "amountFen", "diffAmountFen", "expectedAmountFen"),
                            textOrDefault(dimensions, "diffType", "UNKNOWN"),
                            textOrDefault(dimensions, "status", "OPEN"));
                }, Timestamp.from(from), Timestamp.from(to));
    }

    @Override
    public List<DailyReportQuality> listDailyReportQuality(LocalDate reportDate) {
        return jdbc.query("SELECT rule_code,expected_value,actual_value,status FROM metrics_db.quality_result "
                        + "WHERE task_code='TPLUS1' AND data_date=? ORDER BY checked_at ASC",
                (rs, rowNum) -> new DailyReportQuality(rs.getString("rule_code"),
                        qualityDefinition(rs.getString("rule_code")), rs.getBigDecimal("actual_value"),
                        rs.getBigDecimal("expected_value"), rs.getString("status")),
                java.sql.Date.valueOf(reportDate));
    }

    @Override
    public List<DailyReportAlert> listDailyReportAlerts(Instant from, Instant to) {
        return jdbc.query("SELECT alert_id,severity,rule_code,last_reason,status,opened_at FROM metrics_db.monitor_alert "
                        + "WHERE opened_at>=? AND opened_at<? ORDER BY opened_at DESC LIMIT 100",
                (rs, rowNum) -> new DailyReportAlert(rs.getString("alert_id"), rs.getString("severity"),
                        firstText(rs.getString("last_reason"), rs.getString("rule_code")),
                        rs.getTimestamp("opened_at").toInstant(),
                        rs.getString("last_reason") == null ? "待处理" : rs.getString("last_reason"),
                        rs.getString("status")), Timestamp.from(from), Timestamp.from(to));
    }

    private static String qualityDefinition(String ruleCode) {
        return switch (ruleCode) {
            case "INBOX_COMPLETE" -> "事件消费完整性";
            case "EVENT_QUARANTINE_EMPTY" -> "隔离事件数量";
            default -> "质量检查结果";
        };
    }

    private static JsonNode readJson(String value) {
        try { return JSON.readTree(value == null ? "{}" : value); }
        catch (Exception ignored) { return JSON.createObjectNode(); }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String firstText(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static String textOrDefault(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static long firstLong(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isNumber()) return value.longValue();
            if (value != null && value.isTextual()) {
                try { return Long.parseLong(value.asText()); } catch (NumberFormatException ignored) { }
            }
        }
        return 0L;
    }

    @Override
    public List<DataQualityResult> listDataQuality(LocalDate dataDate, String jobCode, String ruleCode) {
        StringBuilder sql = new StringBuilder("SELECT result_id,task_code,rule_code,status,evidence_json,checked_at "
                + "FROM metrics_db.quality_result WHERE 1=1");
        List<Object> args = new ArrayList<>();
        // OpenAPI 中 dataDate 可选；未指定时按任务与规则筛选全部结果。
        if (dataDate != null) {
            sql.append(" AND data_date=?");
            args.add(java.sql.Date.valueOf(dataDate));
        }
        if (jobCode != null && !jobCode.isBlank()) {
            sql.append(" AND task_code=?");
            args.add(jobCode);
        }
        if (ruleCode != null && !ruleCode.isBlank()) {
            sql.append(" AND rule_code=?");
            args.add(ruleCode);
        }
        sql.append(" ORDER BY checked_at DESC");
        return jdbc.query(sql.toString(), (rs, rowNum) -> mapDataQuality(rs), args.toArray());
    }

    @Override
    public List<MetricDefinition> listMetricDefinitions() {
        return jdbc.query("SELECT metric_code,version,name,unit,formula FROM metrics_db.metric_definition "
                + "WHERE status='ACTIVE' ORDER BY metric_code ASC,version DESC",
                (rs, rowNum) -> new MetricDefinition(rs.getString("metric_code"),
                        String.valueOf(rs.getInt("version")), rs.getString("name"),
                        rs.getString("unit"), rs.getString("formula")));
    }

    @Override
    public List<AlertRule> listAlertRules() {
        return jdbc.query("SELECT rule_code,rule_name,metric_code,severity,operator,threshold_value,enabled,version,updated_by,updated_at "
                + "FROM metrics_db.monitor_alert_rule ORDER BY rule_code ASC",
                (rs, rowNum) -> mapAlertRule(rs));
    }

    @Override
    public Optional<AlertRule> findAlertRule(String ruleCode) {
        return jdbc.query("SELECT rule_code,rule_name,metric_code,severity,operator,threshold_value,enabled,version,updated_by,updated_at "
                + "FROM metrics_db.monitor_alert_rule WHERE rule_code=?",
                rs -> rs.next() ? Optional.of(mapAlertRule(rs)) : Optional.empty(), ruleCode);
    }

    @Override
    public boolean updateAlertRuleThreshold(AlertRule rule, long expectedVersion) {
        return jdbc.update("UPDATE metrics_db.monitor_alert_rule SET threshold_value=?,version=?,updated_by=?,updated_at=? "
                        + "WHERE rule_code=? AND version=?",
                rule.thresholdValue(), rule.version(), rule.updatedBy(), Timestamp.from(rule.updatedAt()),
                rule.ruleCode(), expectedVersion) == 1;
    }

    private static AlertRule mapAlertRule(ResultSet rs) throws SQLException {
        return new AlertRule(rs.getString("rule_code"), rs.getString("rule_name"), rs.getString("metric_code"),
                rs.getString("severity"), rs.getString("operator"), rs.getLong("threshold_value"),
                rs.getBoolean("enabled"), rs.getLong("version"), rs.getString("updated_by"),
                rs.getTimestamp("updated_at").toInstant());
    }

    @Override
    public Optional<AlertOpsIdempotencyRecord> findAlertOpsIdempotency(String operatorId, String idempotencyKey) {
        return jdbc.query("SELECT request_digest,alert_id,alert_type,severity,status,alert_operator_id,last_reason,"
                        + "alert_version,alert_created_at,alert_updated_at "
                        + "FROM metrics_db.alert_ops_idempotency WHERE operator_id=? AND idempotency_key=?",
                rs -> rs.next() ? Optional.of(new AlertOpsIdempotencyRecord(rs.getBytes("request_digest"),
                        rs.getString("alert_id") == null ? null : new Alert(rs.getString("alert_id"),
                                rs.getString("alert_type"), rs.getString("severity"),
                                AlertStatus.valueOf(rs.getString("status")), rs.getString("alert_operator_id"),
                                rs.getString("last_reason"), rs.getLong("alert_version"),
                                rs.getTimestamp("alert_created_at").toInstant(),
                                rs.getTimestamp("alert_updated_at").toInstant())))
                        : Optional.empty(), operatorId, idempotencyKey);
    }

    @Override
    public boolean reserveAlertOpsIdempotency(String recordId, String operatorId, String idempotencyKey, byte[] requestHash) {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.update("INSERT IGNORE INTO metrics_db.alert_ops_idempotency "
                        + "(record_id,operator_id,idempotency_key,request_digest,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?)",
                recordId, operatorId, idempotencyKey, requestHash, now, now) == 1;
    }

    @Override
    public void completeAlertOpsIdempotency(String operatorId, String idempotencyKey, Alert alert) {
        jdbc.update("UPDATE metrics_db.alert_ops_idempotency SET alert_id=?,alert_type=?,severity=?,status=?,"
                        + "alert_operator_id=?,last_reason=?,alert_version=?,alert_created_at=?,alert_updated_at=?,"
                        + "updated_at=? WHERE operator_id=? AND idempotency_key=?",
                alert.getAlertId(), alert.getAlertType(), alert.getSeverity(), alert.getStatus().name(),
                alert.getOperatorId(), alert.getLastReason(), alert.getVersion(),
                Timestamp.from(alert.getCreatedAt()), Timestamp.from(alert.getUpdatedAt()),
                Timestamp.from(clock.instant()), operatorId, idempotencyKey);
    }

    private static DailyMetric mapDailyMetric(ResultSet rs) throws SQLException {
        return new DailyMetric(rs.getString("metric_code"),
                rs.getDate("metric_date").toLocalDate(),
                rs.getBigDecimal("value_decimal").longValue(),
                String.valueOf(rs.getInt("version")),
                rs.getString("quality_status"));
    }

    private Alert mapAlert(ResultSet rs) throws SQLException {
        return new Alert(rs.getString("alert_id"), rs.getString("rule_code"), rs.getString("severity"),
                AlertStatus.valueOf(rs.getString("status")), rs.getString("assignee_id"),
                rs.getString("last_reason"), rs.getLong("version"),
                rs.getTimestamp("opened_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private DataQualityResult mapDataQuality(ResultSet rs) throws SQLException {
        long checkedCount = 0L;
        long failedCount = 0L;
        try {
            JsonNode evidence = JSON.readTree(rs.getString("evidence_json"));
            checkedCount = evidence.path("checkedCount").asLong(0L);
            failedCount = evidence.path("failedCount").asLong(0L);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // 历史数据可能没有证据字段，按状态推断：失败结果至少失败一项。
            if ("FAILED".equals(rs.getString("status"))) failedCount = 1L;
        }
        return new DataQualityResult(rs.getString("result_id"), rs.getString("task_code"),
                rs.getString("rule_code"), rs.getString("status"), checkedCount, failedCount,
                rs.getTimestamp("checked_at").toInstant());
    }

    private record AnalyticsRow(String eventType, Instant occurredAt) { }
    private record MinuteBucket(String metricCode, Instant bucketAt) { }
}
