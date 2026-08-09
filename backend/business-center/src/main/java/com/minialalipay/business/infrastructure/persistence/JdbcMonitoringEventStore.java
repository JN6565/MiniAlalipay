package com.minialalipay.business.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.business.application.monitoring.MonitoringEvent;
import com.minialalipay.business.application.monitoring.MonitoringEventStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.List;

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
    /** 单次失败允许的最大重试次数，超过后仍保留 FAILED 供人工处置。 */
    private static final int MAX_RETRY_COUNT = 8;
    /**
     * 当前分析事实绑定的指标口径版本。
     *
     * <p>事件 Schema 版本与指标口径版本分别演进，不能互相替代。当前 MVP 仅发布第一版指标口径，
     * 因此分析事件固定写入版本 1；后续引入口径灰度时必须由发布策略选择对应版本。</p>
     */
    private static final int ANALYTICS_DEFINITION_VERSION = 1;

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
    public List<String> findRetryableEventIds(Instant now, int limit) {
        return jdbc.query("SELECT event_id FROM metrics_db.inbox_event WHERE consumer_name='ops-projection' "
                        + "AND status='FAILED' AND retry_count<? AND ((next_retry_at IS NULL AND updated_at<=?) OR next_retry_at<=?) "
                        + "ORDER BY updated_at ASC LIMIT ?",
                (rs, rowNum) -> rs.getString(1), MAX_RETRY_COUNT,
                Timestamp.from(now.minusSeconds(retryAfterSeconds)), Timestamp.from(now), limit);
    }

    @Override
    public InboxClaimResult claim(String consumerName, String eventId) {
        Instant now = clock.instant();
        // 新事件直接预占为 PROCESSING；已存在记录不能重复领取，失败达到重试时间的记录允许重新领取，
        // 避免一次临时异常造成永久投影缺口。
        int inserted = jdbc.update("INSERT IGNORE INTO metrics_db.inbox_event "
                        + "(consumer_name,event_id,status,received_at,updated_at,next_retry_at) VALUES (?,?,?,?,?,NULL)",
                consumerName, eventId, "PROCESSING", Timestamp.from(now), Timestamp.from(now));
        if (inserted == 1) return InboxClaimResult.CLAIMED;
        int retried = jdbc.update("UPDATE metrics_db.inbox_event SET status='PROCESSING',updated_at=?,next_retry_at=NULL "
                        + "WHERE consumer_name=? AND event_id=? AND status='FAILED' "
                        + "AND retry_count<? AND ((next_retry_at IS NULL AND updated_at<=?) OR next_retry_at<=?)",
                Timestamp.from(now), consumerName, eventId, MAX_RETRY_COUNT,
                Timestamp.from(now.minusSeconds(retryAfterSeconds)), Timestamp.from(now));
        if (retried == 1) return InboxClaimResult.CLAIMED;
        String status = jdbc.query("SELECT status FROM metrics_db.inbox_event WHERE consumer_name=? AND event_id=?",
                rs -> rs.next() ? rs.getString(1) : null, consumerName, eventId);
        // 只有 DONE 表示该消息已经形成终态投影；FAILED/PROCESSING 必须保留 Stream 游标等待恢复。
        return "DONE".equals(status) ? InboxClaimResult.ALREADY_DONE : InboxClaimResult.RETRY_LATER;
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
                            + "(event_id,event_type,event_version,business_type,occurred_at,definition_version,"
                            + "dimensions_json,metrics_json,trace_id) VALUES (?,?,?,?,?,?,?,?,?)",
                    event.eventId(), event.eventType(), event.version(), businessType, Timestamp.from(event.occurredAt()),
                    ANALYTICS_DEFINITION_VERSION, dimensions, dimensions, event.traceId());
            projectFinalTransaction(event);
        } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
            throw new IllegalStateException("监控事件属性无法序列化", impossible);
        }
    }

    /**
     * 将资金交易事件归并为按交易号唯一的最终状态投影。
     *
     * <p>分析事件保留完整审计轨迹，不能直接作为金额汇总来源；同一交易会经历受理、处理中和终态等多个事件。
     * 此处以事件发生时间和事件号拒绝乱序旧消息覆盖新状态，保证看板与 T+1 日报只统计一次最终结果。</p>
     */
    private void projectFinalTransaction(MonitoringEvent event) {
        if (!"transaction.accepted".equals(event.eventType()) && !"transaction.status.changed".equals(event.eventType())) {
            return;
        }
        Map<String, String> attributes = event.attributes();
        String status = attributes.get("status");
        Timestamp occurredAt = Timestamp.from(event.occurredAt());
        Timestamp acceptedAt = "transaction.accepted".equals(event.eventType()) ? occurredAt : null;
        Timestamp terminalAt = isTerminalStatus(status) ? occurredAt : null;
        String transactionId = attributes.get("transactionId");
        long amountFen = parseLong(attributes.get("amountFen"));
        if (updateFinalTransactionIfNewer(transactionId, amountFen, attributes.get("businessType"), status,
                terminalAt, occurredAt, event.eventId()) == 0) {
            try {
                jdbc.update("INSERT INTO metrics_db.monitoring_transaction_final_projection "
                                + "(transaction_id,amount_fen,business_type,status,accepted_at,terminal_at,source_occurred_at,source_event_id,updated_at) "
                                + "VALUES (?,?,?,?,?,?,?,?,?)",
                        transactionId, amountFen, attributes.get("businessType"), status, acceptedAt, terminalAt,
                        occurredAt, event.eventId(), occurredAt);
            } catch (DuplicateKeyException ignored) {
                // 并发消费者已先插入同一交易时，只允许本事件在时间更新条件满足后覆盖。
                updateFinalTransactionIfNewer(transactionId, amountFen, attributes.get("businessType"), status,
                        terminalAt, occurredAt, event.eventId());
            }
        }
        if (acceptedAt != null) {
            // 受理事件可能晚于终态事件到达；它只能补齐受理时间，绝不能回退当前终态。
            jdbc.update("UPDATE metrics_db.monitoring_transaction_final_projection SET accepted_at=COALESCE(accepted_at,?) "
                    + "WHERE transaction_id=?", acceptedAt, transactionId);
        }
    }

    private int updateFinalTransactionIfNewer(String transactionId, long amountFen, String businessType, String status,
                                               Timestamp terminalAt, Timestamp occurredAt, String eventId) {
        return jdbc.update("UPDATE metrics_db.monitoring_transaction_final_projection SET "
                        + "amount_fen=CASE WHEN ?>0 THEN ? ELSE amount_fen END,business_type=COALESCE(?,business_type),"
                        + "status=?,terminal_at=CASE WHEN ? IS NOT NULL THEN ? ELSE terminal_at END,"
                        + "source_occurred_at=?,source_event_id=?,updated_at=? WHERE transaction_id=? AND "
                        + "(source_occurred_at<? OR (source_occurred_at=? AND source_event_id<?))",
                amountFen, amountFen, businessType, status, terminalAt, terminalAt, occurredAt, eventId, occurredAt,
                transactionId, occurredAt, occurredAt, eventId);
    }

    private static boolean isTerminalStatus(String status) {
        return "SUCCESS".equals(status) || "REVERSED".equals(status) || "CANCELLED".equals(status);
    }

    @Override
    public void complete(String consumerName, String eventId) {
        jdbc.update("UPDATE metrics_db.inbox_event SET status='DONE',updated_at=? "
                + "WHERE consumer_name=? AND event_id=? AND status='PROCESSING'",
                Timestamp.from(clock.instant()), consumerName, eventId);
    }

    @Override
    public void fail(String consumerName, String eventId, String reason) {
        Instant now = clock.instant();
        String safeReason = reason == null || reason.isBlank() ? "投影失败" : reason;
        if (safeReason.length() > 512) safeReason = safeReason.substring(0, 512);
        jdbc.update("UPDATE metrics_db.inbox_event SET status='FAILED',failure_reason=?,retry_count=retry_count+1,"
                        + "last_failed_at=?,next_retry_at=?,updated_at=? "
                        + "WHERE consumer_name=? AND event_id=?",
                safeReason, Timestamp.from(now), Timestamp.from(now.plusSeconds(retryDelaySeconds(currentRetryCount(consumerName, eventId)))),
                Timestamp.from(now), consumerName, eventId);
    }

    private int currentRetryCount(String consumerName, String eventId) {
        Integer count = jdbc.query("SELECT retry_count FROM metrics_db.inbox_event WHERE consumer_name=? AND event_id=?",
                rs -> rs.next() ? rs.getInt(1) : 0, consumerName, eventId);
        return count == null ? 0 : count;
    }

    private long retryDelaySeconds(int retryCount) {
        int exponent = Math.min(Math.max(retryCount, 0), 6);
        return Math.min(300L, retryAfterSeconds * (1L << exponent));
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

    @Override
    public String currentStreamCursor(String consumerName) {
        Instant now = clock.instant();
        jdbc.update("INSERT IGNORE INTO metrics_db.monitoring_stream_checkpoint "
                        + "(consumer_name,stream_cursor,updated_at) VALUES (?,'0-0',?)",
                consumerName, Timestamp.from(now));
        return jdbc.query("SELECT stream_cursor FROM metrics_db.monitoring_stream_checkpoint WHERE consumer_name=?",
                rs -> rs.next() ? rs.getString(1) : "0-0", consumerName);
    }

    @Override
    public boolean advanceStreamCursor(String consumerName, String expectedCursor, String nextCursor) {
        return jdbc.update("UPDATE metrics_db.monitoring_stream_checkpoint SET stream_cursor=?,updated_at=? "
                        + "WHERE consumer_name=? AND stream_cursor=?",
                nextCursor, Timestamp.from(clock.instant()), consumerName, expectedCursor) == 1;
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
