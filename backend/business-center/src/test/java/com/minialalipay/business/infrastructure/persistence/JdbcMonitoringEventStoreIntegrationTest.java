package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.monitoring.MonitoringEvent;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static com.minialalipay.business.application.monitoring.MonitoringEventStore.InboxClaimResult.ALREADY_DONE;
import static com.minialalipay.business.application.monitoring.MonitoringEventStore.InboxClaimResult.CLAIMED;
import static com.minialalipay.business.application.monitoring.MonitoringEventStore.InboxClaimResult.RETRY_LATER;

/** 监控 Inbox 消费者与投影仓储的 H2 集成测试：幂等领取、失败重试和事件投影落表。 */
class JdbcMonitoringEventStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    private JdbcTemplate jdbc;
    private JdbcMonitoringEventStore store;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        store = new JdbcMonitoringEventStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC), 30L);
    }

    @Test
    void 新事件领取成功后标记完成且重复投递不重复领取() {
        MonitoringEvent event = event("event-1", "alert.status.changed",
                Map.of("alertId", "alert-1", "status", "OPEN"));

        assertThat(store.claim("consumer-1", "event-1")).isEqualTo(CLAIMED);
        assertThat(store.claim("consumer-1", "event-1")).isEqualTo(RETRY_LATER);
        store.project(event);
        store.complete("consumer-1", "event-1");
        assertThat(status("consumer-1", "event-1")).isEqualTo("DONE");
        assertThat(store.claim("consumer-1", "event-1")).isEqualTo(ALREADY_DONE);
    }

    @Test
    void 失败记录达到重试时间后可重新领取() {
        jdbc.update("INSERT INTO metrics_db.inbox_event (consumer_name,event_id,status,received_at,updated_at) "
                + "VALUES ('consumer-1','event-old','FAILED',?,?)",
                Timestamp.from(NOW.minusSeconds(120)), Timestamp.from(NOW.minusSeconds(120)));

        assertThat(store.claim("consumer-1", "event-old")).isEqualTo(CLAIMED);
        assertThat(status("consumer-1", "event-old")).isEqualTo("PROCESSING");
    }

    @Test
    void 失败记录未到退避时间时要求消费者保留流游标() {
        jdbc.update("INSERT INTO metrics_db.inbox_event (consumer_name,event_id,status,received_at,updated_at) "
                        + "VALUES ('consumer-1','event-waiting','FAILED',?,?)",
                Timestamp.from(NOW), Timestamp.from(NOW));

        assertThat(store.claim("consumer-1", "event-waiting")).isEqualTo(RETRY_LATER);
    }

    @Test
    void 失败记录保存原因并写入重试时间() {
        store.claim("consumer-1", "event-failed");
        store.fail("consumer-1", "event-failed", "数据库连接暂时不可用");

        assertThat(jdbc.queryForObject("SELECT failure_reason FROM metrics_db.inbox_event "
                + "WHERE consumer_name='consumer-1' AND event_id='event-failed'", String.class))
                .isEqualTo("数据库连接暂时不可用");
        assertThat(jdbc.queryForObject("SELECT retry_count FROM metrics_db.inbox_event "
                + "WHERE consumer_name='consumer-1' AND event_id='event-failed'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 告警与数据质量事件投影到对应投影表() {
        store.project(event("event-alert", "alert.status.changed",
                Map.of("alertId", "alert-1", "status", "OPEN", "alertType", "TCC_TIMEOUT", "severity", "CRITICAL")));
        store.project(event("event-quality", "data_quality.check.completed",
                Map.of("resultId", "quality-1", "status", "PASSED", "checkType", "交易完整性",
                        "jobCode", "job-1", "dataDate", "2026-08-04", "ruleCode", "rule-1",
                        "checkedCount", "100", "failedCount", "0")));

        assertThat(jdbc.queryForObject("SELECT status FROM metrics_db.monitor_alert WHERE alert_id='alert-1'", String.class))
                .isEqualTo("OPEN");
        assertThat(jdbc.queryForObject("SELECT task_code FROM metrics_db.quality_result WHERE result_id='quality-1'", String.class))
                .isEqualTo("job-1");
        assertThat(jdbc.queryForObject("SELECT evidence_json FROM metrics_db.quality_result WHERE result_id='quality-1'", String.class))
                .contains("\"checkedCount\":100");
    }

    @Test
    void 交易与对账事件投影为分析事件并记录请求编号() {
        store.project(event("event-tx", "transaction.status.changed",
                Map.of("transactionId", "tx-1", "status", "SUCCESS", "businessType", "TRANSFER")));

        assertThat(jdbc.queryForObject("SELECT event_type FROM metrics_db.analytics_event WHERE event_id='event-tx'", String.class))
                .isEqualTo("transaction.status.changed");
        assertThat(jdbc.queryForObject("SELECT trace_id FROM metrics_db.analytics_event WHERE event_id='event-tx'", String.class))
                .isEqualTo("trace-1");
        assertThat(jdbc.queryForObject("SELECT event_version FROM metrics_db.analytics_event WHERE event_id='event-tx'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT definition_version FROM metrics_db.analytics_event WHERE event_id='event-tx'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 契约不合法的事件进入隔离表() {
        store.quarantine(event("event-bad", "alert.status.changed",
                Map.of("alertId", "alert-1", "paymentPassword", "secret")), "事件包含禁止的敏感字段");

        assertThat(jdbc.queryForObject("SELECT status FROM metrics_db.quarantined_event "
                + "WHERE consumer_name='ops-projection' AND event_id='event-bad'", String.class)).isEqualTo("OPEN");
    }

    private static MonitoringEvent event(String id, String type, Map<String, String> attributes) {
        return new MonitoringEvent(id, type, 1, NOW, "trace-1", attributes);
    }

    private String status(String consumer, String eventId) {
        return jdbc.queryForObject("SELECT status FROM metrics_db.inbox_event WHERE consumer_name=? AND event_id=?",
                String.class, consumer, eventId);
    }

    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:monitoring_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static void createTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS metrics_db");
        jdbc.execute("CREATE TABLE metrics_db.inbox_event (consumer_name VARCHAR(64) NOT NULL,event_id VARCHAR(26) NOT NULL,"
                + "status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',received_at TIMESTAMP NOT NULL,updated_at TIMESTAMP NOT NULL,"
                + "failure_reason VARCHAR(512),retry_count INT NOT NULL DEFAULT 0,next_retry_at TIMESTAMP,last_failed_at TIMESTAMP,"
                + "PRIMARY KEY (consumer_name,event_id))");
        jdbc.execute("CREATE TABLE metrics_db.analytics_event (event_id VARCHAR(64) PRIMARY KEY,event_type VARCHAR(64) NOT NULL,"
                + "event_version SMALLINT NOT NULL,business_type VARCHAR(16),occurred_at TIMESTAMP NOT NULL,"
                + "definition_version INT NOT NULL,dimensions_json VARCHAR(512),metrics_json VARCHAR(512),trace_id VARCHAR(32) NOT NULL)");
        jdbc.execute("CREATE TABLE metrics_db.quarantined_event (consumer_name VARCHAR(64),event_id VARCHAR(64),"
                + "reason_code VARCHAR(32),schema_version INT,payload VARCHAR(512),status VARCHAR(16),quarantined_at TIMESTAMP,"
                + "PRIMARY KEY (consumer_name,event_id))");
        jdbc.execute("CREATE TABLE metrics_db.monitor_alert (alert_id VARCHAR(26) PRIMARY KEY,rule_code VARCHAR(64),"
                + "severity VARCHAR(8),status VARCHAR(16),subject_id VARCHAR(128),evidence_json VARCHAR(512),"
                + "assignee_id VARCHAR(26),last_reason VARCHAR(256),version BIGINT,opened_at TIMESTAMP,updated_at TIMESTAMP,closed_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE metrics_db.quality_result (result_id VARCHAR(26) PRIMARY KEY,task_code VARCHAR(64),"
                + "data_date DATE,rule_code VARCHAR(64),status VARCHAR(16),evidence_json VARCHAR(512),checked_at TIMESTAMP)");
    }
}
