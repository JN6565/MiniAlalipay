package com.minialalipay.business.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.business.application.monitoring.MonitoringEvent;
import com.minialalipay.business.application.monitoring.MonitoringEventStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * metrics_db 的监控 Inbox 与投影仓储实现。
 *
 * <p>同一事务内完成领取、投影和完成标记；只写 metrics_db 投影，不得调用账户中心或修改业务资金事实。
 * 告警、质量结果和分析事件的投影值只来源于事件载荷中已脱敏的结构化字段。</p>
 */
@Repository
public class JdbcMonitoringEventStore implements MonitoringEventStore {
    private static final ObjectMapper JSON = new ObjectMapper();
    /** 失败 Inbox 记录允许重新领取的最短等待时间。 */
    private static final long DEFAULT_RETRY_AFTER_SECONDS = 30;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final long retryAfterSeconds;

    /** 创建监控事件仓储。 */
    @Autowired
    public JdbcMonitoringEventStore(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC(), DEFAULT_RETRY_AFTER_SECONDS);
    }

    JdbcMonitoringEventStore(JdbcTemplate jdbc, Clock clock, long retryAfterSeconds) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    @Override
    public boolean claim(String consumerName, String eventId) {
        Instant now = clock.instant();
        // 新事件直接预占为 PROCESSING；已存在记录不能重复领取，失败达到重试时间的记录允许重新领取，
        // 避免一次临时异常造成永久投影缺口。
        int inserted = jdbc.update("INSERT IGNORE INTO metrics_db.inbox_event "
                        + "(consumer_name,event_id,status,received_at,updated_at) VALUES (?,?,?,?,?)",
                consumerName, eventId, "PROCESSING", Timestamp.from(now), Timestamp.from(now));
        if (inserted == 1) return true;
        return jdbc.update("UPDATE metrics_db.inbox_event SET status='PROCESSING',updated_at=? "
                        + "WHERE consumer_name=? AND event_id=? AND status='FAILED' AND updated_at<=?",
                Timestamp.from(now), consumerName, eventId,
                Timestamp.from(now.minusSeconds(retryAfterSeconds))) == 1;
    }

    @Override
    public void project(MonitoringEvent event) {
        switch (event.eventType()) {
            case "alert.status.changed" -> projectAlert(event);
            case "data_quality.check.completed" -> projectDataQuality(event);
            default -> projectAnalytics(event);
        }
    }

    private void projectAlert(MonitoringEvent event) {
        Map<String, String> attributes = event.attributes();
        String reason = attributes.getOrDefault("reason", "");
        String severity = attributes.getOrDefault("severity", "INFO");
        String alertType = attributes.getOrDefault("alertType", attributes.get("alertId"));
        // 事件是外部权威状态来源：已存在则刷新状态与理由，不存在则新建开放告警。
        jdbc.update("INSERT INTO metrics_db.monitor_alert "
                        + "(alert_id,rule_code,severity,status,subject_id,evidence_json,last_reason,version,opened_at,updated_at) "
                        + "VALUES (?,?,?,?,NULL,'{}',?,0,?,?) "
                        + "ON DUPLICATE KEY UPDATE status=VALUES(status),last_reason=VALUES(last_reason),updated_at=VALUES(updated_at)",
                attributes.get("alertId"), alertType, severity, attributes.get("status"), reason,
                Timestamp.from(event.occurredAt()), Timestamp.from(event.occurredAt()));
    }

    private void projectDataQuality(MonitoringEvent event) {
        Map<String, String> attributes = event.attributes();
        long checkedCount = parseLong(attributes.get("checkedCount"));
        long failedCount = parseLong(attributes.get("failedCount"));
        String evidence = "{\"checkedCount\":" + checkedCount + ",\"failedCount\":" + failedCount + "}";
        jdbc.update("INSERT INTO metrics_db.quality_result "
                        + "(result_id,task_code,data_date,rule_code,status,evidence_json,checked_at) "
                        + "VALUES (?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE status=VALUES(status),evidence_json=VALUES(evidence_json),checked_at=VALUES(checked_at)",
                attributes.get("resultId"), attributes.getOrDefault("jobCode", attributes.getOrDefault("checkType", "")),
                java.sql.Date.valueOf(attributes.getOrDefault("dataDate", "1970-01-01")),
                attributes.getOrDefault("ruleCode", ""), attributes.get("status"), evidence,
                Timestamp.from(event.occurredAt()));
    }

    private void projectAnalytics(MonitoringEvent event) {
        Map<String, String> attributes = event.attributes();
        try {
            String dimensions = JSON.writeValueAsString(attributes);
            String businessType = attributes.get("businessType");
            jdbc.update("INSERT INTO metrics_db.analytics_event "
                            + "(event_id,event_type,business_type,occurred_at,dimensions_json,metrics_json,trace_id) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    event.eventId(), event.eventType(), businessType, Timestamp.from(event.occurredAt()),
                    dimensions, dimensions, event.traceId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException("监控事件属性无法序列化", impossible);
        }
    }

    @Override
    public void complete(String consumerName, String eventId) {
        jdbc.update("UPDATE metrics_db.inbox_event SET status='DONE',updated_at=? "
                + "WHERE consumer_name=? AND event_id=? AND status='PROCESSING'",
                Timestamp.from(clock.instant()), consumerName, eventId);
    }

    @Override
    public void fail(String consumerName, String eventId, String reason) {
        jdbc.update("UPDATE metrics_db.inbox_event SET status='FAILED',updated_at=? "
                + "WHERE consumer_name=? AND event_id=?",
                Timestamp.from(clock.instant()), consumerName, eventId);
    }

    @Override
    public void quarantine(MonitoringEvent event, String reason) {
        try {
            String payload = JSON.writeValueAsString(event.attributes());
            String reasonCode = (reason == null || reason.isBlank()) ? "UNKNOWN"
                    : reason.length() > 32 ? reason.substring(0, 32) : reason;
            jdbc.update("INSERT INTO metrics_db.quarantined_event "
                            + "(consumer_name,event_id,reason_code,schema_version,payload,status,quarantined_at) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    "ops-projection", event.eventId(), reasonCode, event.version(), payload, "OPEN",
                    Timestamp.from(clock.instant()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException("隔离事件载荷无法序列化", impossible);
        }
    }

    private static long parseLong(String value) {
        if (value == null) return 0L;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
