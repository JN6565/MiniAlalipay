package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.domain.monitoring.Alert;
import com.minialalipay.business.domain.monitoring.AlertRule;
import com.minialalipay.business.domain.monitoring.AlertStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 监控投影查询与告警处置仓储的 H2 集成测试。 */
class JdbcMonitoringProjectionStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    private JdbcTemplate jdbc;
    private JdbcMonitoringProjectionStore store;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        store = new JdbcMonitoringProjectionStore(jdbc);
    }

    @Test
    void 告警按状态分页查询且CAS更新校验版本() {
        jdbc.update("INSERT INTO metrics_db.monitor_alert (alert_id,rule_code,severity,status,version,opened_at,updated_at) "
                + "VALUES ('alert-1','TCC_TIMEOUT','CRITICAL','OPEN',0,?,?)", Timestamp.from(NOW), Timestamp.from(NOW));

        assertThat(store.listAlerts("OPEN", null, null, 50)).hasSize(1);
        assertThat(store.listAlerts(null, "CRITICAL", null, 50)).hasSize(1);
        assertThat(store.listAlerts(null, "INFO", null, 50)).isEmpty();
        Alert alert = store.findAlert("alert-1").orElseThrow();
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.OPEN);

        assertThat(store.updateAlert(alert, 1L)).isFalse();
        assertThat(store.updateAlert(alert, 0L)).isTrue();
        assertThat(jdbc.queryForObject("SELECT version FROM metrics_db.monitor_alert WHERE alert_id='alert-1'", Long.class))
                .isEqualTo(0L);
    }

    @Test
    void 告警处置幂等记录预占完成与回放() {
        // 与 SecurityMaterialPort.digest 一致，使用 SHA-256 的 32 字节摘要。
        byte[] digest = new byte[32];
        for (int i = 0; i < digest.length; i++) digest[i] = (byte) i;
        assertThat(store.reserveAlertOpsIdempotency("record-1", "ops-1", "key-1", digest)).isTrue();
        assertThat(store.reserveAlertOpsIdempotency("record-2", "ops-1", "key-1", digest)).isFalse();
        assertThat(store.findAlertOpsIdempotency("ops-1", "key-1").orElseThrow().result()).isNull();

        Alert alert = Alert.open("alert-1", "TCC_TIMEOUT", "CRITICAL", NOW);
        store.completeAlertOpsIdempotency("ops-1", "key-1", alert);
        MonitoringProjectionStore.AlertOpsIdempotencyRecord replayed =
                store.findAlertOpsIdempotency("ops-1", "key-1").orElseThrow();
        assertThat(replayed.result()).isNotNull();
        assertThat(Arrays.equals(replayed.requestHash(), digest)).isTrue();
    }

    @Test
    void 实时指标按事件类型和分钟聚合() {
        jdbc.update("INSERT INTO metrics_db.analytics_event (event_id,event_type,occurred_at,trace_id) "
                + "VALUES ('a','transaction.status.changed',?,NULL)", Timestamp.from(NOW));
        jdbc.update("INSERT INTO metrics_db.analytics_event (event_id,event_type,occurred_at,trace_id) "
                + "VALUES ('b','transaction.status.changed',?,NULL)", Timestamp.from(NOW.plusSeconds(40)));
        jdbc.update("INSERT INTO metrics_db.analytics_event (event_id,event_type,occurred_at,trace_id) "
                + "VALUES ('c','transaction.status.changed',?,NULL)", Timestamp.from(NOW.plusSeconds(70)));
        jdbc.update("INSERT INTO metrics_db.analytics_event (event_id,event_type,occurred_at,trace_id) "
                + "VALUES ('d','alert.status.changed',?,NULL)", Timestamp.from(NOW.plusSeconds(15)));

        var metrics = store.listRealtimeMetrics(null, NOW.minusSeconds(5), NOW.plusSeconds(120));

        assertThat(metrics).hasSize(3);
        assertThat(metrics).filteredOn(m -> "alert.status.changed".equals(m.metricCode()))
                .singleElement().satisfies(m -> assertThat(m.value()).isEqualTo(1L));
        assertThat(metrics).filteredOn(m -> "transaction.status.changed".equals(m.metricCode()))
                .anySatisfy(m -> assertThat(m.value()).isEqualTo(2L));
    }

    @Test
    void 日报只返回通过质量门禁的指标() {
        jdbc.update("INSERT INTO metrics_db.daily_metric (metric_date,metric_code,dimension_hash,value_decimal,quality_status,version) "
                + "VALUES ('2026-08-04','transaction_success',?,?, 'PASSED',1)",
                new byte[32], new BigDecimal("120"));
        jdbc.update("INSERT INTO metrics_db.daily_metric (metric_date,metric_code,dimension_hash,value_decimal,quality_status,version) "
                + "VALUES ('2026-08-04','reconcile_diff',?,?, 'FAILED',1)",
                new byte[32], new BigDecimal("3"));

        var reports = store.listDailyReports(LocalDate.of(2026, 8, 4));
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).metricCode()).isEqualTo("transaction_success");
        assertThat(reports.get(0).value()).isEqualTo(120L);
    }

    @Test
    void 数据质量结果按日期任务规则过滤并解析数量() {
        jdbc.update("INSERT INTO metrics_db.quality_result (result_id,task_code,data_date,rule_code,status,evidence_json,checked_at) "
                + "VALUES ('q-1','交易完整性','2026-08-04','rule-1','PASSED',?,?)",
                "{\"checkedCount\":100,\"failedCount\":0}", Timestamp.from(NOW));

        var results = store.listDataQuality(LocalDate.of(2026, 8, 4), "交易完整性", "rule-1");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).checkedCount()).isEqualTo(100L);
        assertThat(results.get(0).failedCount()).isZero();
    }

    @Test
    void 指标定义只返回激活版本() {
        jdbc.update("INSERT INTO metrics_db.metric_definition (metric_code,version,name,unit,formula,status,effective_at) "
                + "VALUES ('transaction_success',1,'交易成功量','笔','COUNT(transaction_id)','ACTIVE',?)", Timestamp.from(NOW));
        jdbc.update("INSERT INTO metrics_db.metric_definition (metric_code,version,name,unit,formula,status,effective_at) "
                + "VALUES ('reconcile_diff',1,'对账差异','笔','COUNT(diff_id)','RETIRED',?)", Timestamp.from(NOW));

        var definitions = store.listMetricDefinitions();
        assertThat(definitions).hasSize(1);
        assertThat(definitions.get(0).metricCode()).isEqualTo("transaction_success");
        assertThat(definitions.get(0).unit()).isEqualTo("笔");
    }

    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:monitoring_projection_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static void createTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS metrics_db");
        jdbc.execute("CREATE TABLE metrics_db.analytics_event (event_id VARCHAR(64) PRIMARY KEY,event_type VARCHAR(64),"
                + "business_type VARCHAR(16),occurred_at TIMESTAMP,dimensions_json VARCHAR(512),metrics_json VARCHAR(512),trace_id VARCHAR(32))");
        jdbc.execute("CREATE TABLE metrics_db.monitor_alert (alert_id VARCHAR(26) PRIMARY KEY,rule_code VARCHAR(64),"
                + "severity VARCHAR(8),status VARCHAR(16),subject_id VARCHAR(128),evidence_json VARCHAR(512),"
                + "assignee_id VARCHAR(26),last_reason VARCHAR(256),version BIGINT,opened_at TIMESTAMP,updated_at TIMESTAMP,closed_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE metrics_db.quality_result (result_id VARCHAR(26) PRIMARY KEY,task_code VARCHAR(64),"
                + "data_date DATE,rule_code VARCHAR(64),status VARCHAR(16),evidence_json VARCHAR(512),checked_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE metrics_db.metric_definition (metric_code VARCHAR(64),version INT,name VARCHAR(128),"
                + "unit VARCHAR(32),formula VARCHAR(512),dimensions_json VARCHAR(512),owner_id VARCHAR(26),status VARCHAR(16),"
                + "effective_at TIMESTAMP,PRIMARY KEY (metric_code,version))");
        jdbc.execute("CREATE TABLE metrics_db.daily_metric (metric_date DATE,metric_code VARCHAR(64),dimension_hash BINARY(32),"
                + "dimensions_json VARCHAR(512),value_decimal DECIMAL(24,6),quality_status VARCHAR(16),version INT,"
                + "PRIMARY KEY (metric_date,metric_code,dimension_hash,version))");
        jdbc.execute("CREATE TABLE metrics_db.alert_ops_idempotency (record_id VARCHAR(26) PRIMARY KEY,operator_id VARCHAR(64),"
                + "idempotency_key VARCHAR(64),request_digest BINARY(32),alert_id VARCHAR(26),alert_type VARCHAR(64),severity VARCHAR(8),"
                + "status VARCHAR(16),alert_operator_id VARCHAR(64),last_reason VARCHAR(256),alert_version BIGINT,"
                + "alert_created_at TIMESTAMP,alert_updated_at TIMESTAMP,created_at TIMESTAMP,updated_at TIMESTAMP,"
                + "UNIQUE (operator_id,idempotency_key))");
        jdbc.execute("CREATE TABLE metrics_db.monitor_alert_rule (rule_code VARCHAR(64) PRIMARY KEY,rule_name VARCHAR(128),"
                + "metric_code VARCHAR(64),severity VARCHAR(16),operator VARCHAR(8),threshold_value BIGINT,enabled TINYINT,"
                + "version BIGINT,updated_by VARCHAR(64),created_at TIMESTAMP,updated_at TIMESTAMP)");
    }

    @Test
    void 告警规则查询并按CAS更新阈值() {
        jdbc.update("INSERT INTO metrics_db.monitor_alert_rule (rule_code,rule_name,metric_code,severity,operator,threshold_value,enabled,version,updated_by,created_at,updated_at) "
                + "VALUES ('DUPLICATE_CHARGE','重复扣款告警','duplicate_charge_count','CRITICAL','GT',0,1,0,'seed',?,?)",
                Timestamp.from(NOW), Timestamp.from(NOW));

        assertThat(store.listAlertRules()).hasSize(1);
        AlertRule rule = store.findAlertRule("DUPLICATE_CHARGE").orElseThrow();
        assertThat(rule.thresholdValue()).isEqualTo(0L);

        AlertRule next = rule.withThreshold(5, "admin-1", NOW.plusSeconds(1));
        // CAS 版本不匹配时拒绝更新。
        assertThat(store.updateAlertRuleThreshold(next, 1L)).isFalse();
        assertThat(store.updateAlertRuleThreshold(next, 0L)).isTrue();
        assertThat(store.findAlertRule("DUPLICATE_CHARGE").orElseThrow().thresholdValue()).isEqualTo(5L);
        assertThat(jdbc.queryForObject("SELECT updated_by FROM metrics_db.monitor_alert_rule WHERE rule_code='DUPLICATE_CHARGE'", String.class))
                .isEqualTo("admin-1");
    }
}
