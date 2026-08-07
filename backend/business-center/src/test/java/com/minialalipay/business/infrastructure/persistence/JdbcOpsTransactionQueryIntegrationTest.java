package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionQuery;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionRow;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.TraceSpan;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** B 端运营交易查询仓储的 H2 集成测试：脱敏、游标分页、详情关联与链路片段。 */
class JdbcOpsTransactionQueryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    /** 测试固定链路编号（32 位 hex），与跨服务 trace_id 列对齐。 */
    private static final String TRACE = "abcdef0123456789abcdef0123456789";

    private JdbcTemplate jdbc;
    private JdbcBusinessStore store;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        store = new JdbcBusinessStore(jdbc);
    }

    @Test
    void 运营交易列表按游标分页且发起人脱敏() {
        insertTransaction("tx-2", "QR_PAY", "QR_PAY_ORDER", "order-2", "00000000000000000000002345");
        insertTransaction("tx-1", "TRANSFER", "TRANSFER_DRAFT", "order-1", "00000000000000000000001234");

        List<OpsTransactionRow> rows = store.listTransactionsForOps(new OpsTransactionQuery(null, null, null, 10, null, null));
        assertThat(rows).hasSize(2);
        // 游标按交易 ID 倒序：最新交易在前。
        assertThat(rows.get(0).transactionId()).isEqualTo("tx-2");
        assertThat(rows.get(0).initiatorMasked()).isEqualTo("000000***2345");
        assertThat(rows.get(0).amountFen()).isEqualTo(5200L);

        // 游标翻页：以最新交易 tx-2 为游标返回比它更旧的交易。
        List<OpsTransactionRow> page = store.listTransactionsForOps(new OpsTransactionQuery(null, null, "tx-2", 10, null, null));
        assertThat(page).hasSize(1);
        assertThat(page.get(0).transactionId()).isEqualTo("tx-1");
    }

    @Test
    void 交易详情关联TCC全局Outbox事件与活动工单() {
        insertTransaction("tx-1", "TRANSFER", "TRANSFER_DRAFT", "order-1", "00000000000000000000001234");
        jdbc.update("INSERT INTO business_db.tcc_global (transaction_id,xid,status,retry_count,started_at,updated_at) VALUES ('tx-1','xid-1','SUCCESS',1,?,?)",
                Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("INSERT INTO business_db.outbox_event (event_id,aggregate_type,aggregate_id,event_type,transaction_id,producer,trace_id,occurred_at,payload,status) VALUES ('evt-1','FUND_TRANSACTION','tx-1','transaction.status.changed','tx-1','business-center','abcdef0123456789abcdef0123456789',?,'{\"status\":\"SUCCESS\"}','COMPLETED')",
                Timestamp.from(NOW));
        jdbc.update("INSERT INTO business_db.manual_case (case_id,subject_type,subject_id,transaction_id,status,created_at,updated_at) VALUES ('case-1','FUND_TRANSACTION','tx-1','tx-1','OPEN',?,?)",
                Timestamp.from(NOW), Timestamp.from(NOW));

        var detail = store.findTransactionForOps("tx-1").orElseThrow();
        assertThat(detail.row().transactionId()).isEqualTo("tx-1");
        assertThat(detail.fundingSource()).isEqualTo("BALANCE");
        assertThat(detail.tccStatus()).isEqualTo("SUCCESS");
        assertThat(detail.tccRetryCount()).isEqualTo(1);
        assertThat(detail.latestOutboxEventType()).isEqualTo("transaction.status.changed");
        assertThat(detail.outboxStatus()).isEqualTo("COMPLETED");
        assertThat(detail.activeManualCaseId()).isEqualTo("case-1");
    }

    @Test
    void 按链路编号聚合跨服务Span且交易归属明确() {
        insertTransaction("tx-1", "TRANSFER", "TRANSFER_DRAFT", "order-1", "00000000000000000000001234");
        jdbc.update("INSERT INTO business_db.tcc_global (transaction_id,xid,status,retry_count,started_at,updated_at) VALUES ('tx-1','xid-1','SUCCESS',0,?,?)",
                Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("INSERT INTO business_db.outbox_event (event_id,aggregate_type,aggregate_id,event_type,transaction_id,producer,trace_id,occurred_at,payload,status) VALUES ('evt-1','FUND_TRANSACTION','tx-1','transaction.status.changed','tx-1','business-center',?,?,'{\"status\":\"SUCCESS\"}','COMPLETED')",
                TRACE, Timestamp.from(NOW));
        jdbc.update("INSERT INTO ledger_db.outbox_event (event_id,aggregate_type,aggregate_id,event_type,transaction_id,producer,trace_id,occurred_at,payload,status) VALUES ('levt-1','LEDGER','tx-1','ledger.posted','tx-1','account-center',?,?,'{}','COMPLETED')",
                TRACE, Timestamp.from(NOW));
        jdbc.update("INSERT INTO user_db.audit_log (audit_id,actor_type,actor_id,action,target_type,target_id,result_code,trace_id,occurred_at) VALUES (1,'OPERATOR','ops-1','user.view','USER','u-1','SUCCESS',?,?)",
                TRACE, Timestamp.from(NOW));
        jdbc.update("INSERT INTO agent_db.tool_call_log (tool_call_id,session_id,tool_name,request_digest,result_code,duration_ms,trace_id,occurred_at) VALUES ('tool-1','sess-1','balance_query',?,'OK',12,?,?)",
                new byte[32], TRACE, Timestamp.from(NOW));
        jdbc.update("INSERT INTO agent_db.audit_log (audit_id,actor_type,actor_id,action,target_type,target_id,result_code,trace_id,occurred_at) VALUES (2,'SYSTEM','ai','tool.executed','TOOL','tool-1','FAILED',?,?)",
                TRACE, Timestamp.from(NOW));

        List<TraceSpan> spans = store.findTraceSpansByTraceId(TRACE);
        assertThat(spans).hasSize(7);
        assertThat(spans).anyMatch(s -> s.operation().equals("统一交易受理") && "business-center".equals(s.service())
                && "tx-1".equals(s.transactionId()));
        assertThat(spans).anyMatch(s -> s.operation().equals("TCC 全局事务") && "business-center".equals(s.service()));
        assertThat(spans).anyMatch(s -> s.operation().equals("终态事件发布") && "business-center".equals(s.service()));
        assertThat(spans).anyMatch(s -> s.operation().equals("账本过账事件") && "account-center".equals(s.service())
                && "tx-1".equals(s.transactionId()));
        assertThat(spans).anyMatch(s -> s.operation().equals("用户中心审计") && "user-center".equals(s.service())
                && s.transactionId() == null);
        assertThat(spans).anyMatch(s -> s.operation().equals("AI 工具调用") && "ai-service".equals(s.service())
                && s.status().equals("SUCCESS"));
        assertThat(spans).anyMatch(s -> s.operation().equals("AI 审计") && "ai-service".equals(s.service())
                && s.status().equals("ERROR"));
    }

    @Test
    void 未知链路编号返回空列表() {
        assertThat(store.findTraceSpansByTraceId("ffffffffffffffffffffffffffffffff")).isEmpty();
        assertThat(store.findTraceSpansByTraceId(null)).isEmpty();
        assertThat(store.findTransactionForOps("missing")).isEmpty();
    }

    private void insertTransaction(String id, String businessType, String sourceType, String sourceOrderId, String initiator) {
        jdbc.update("INSERT INTO business_db.fund_transaction (transaction_id,business_type,source_type,source_order_id,initiator_user_id,payer_account_id,payee_account_id,funding_source,amount_fen,idempotency_key,status,risk_level,trace_id,version,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,'BALANCE',5200,'key-1','SUCCESS','LOW',?,0,?,?)",
                id, businessType, sourceType, sourceOrderId, initiator, "payer-1", "payee-1",
                "abcdef0123456789abcdef0123456789", Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private static JdbcDataSource dataSource() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:ops_tx_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return ds;
    }

    private static void createTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS business_db");
        jdbc.execute("CREATE TABLE business_db.fund_transaction (transaction_id VARCHAR(26) PRIMARY KEY,business_type VARCHAR(16),source_type VARCHAR(32),source_order_id VARCHAR(26),initiator_user_id VARCHAR(26),payer_account_id VARCHAR(26),payee_account_id VARCHAR(26),funding_source VARCHAR(16),amount_fen BIGINT,idempotency_key VARCHAR(64),status VARCHAR(32),risk_level VARCHAR(16),trace_id CHAR(32),version BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.tcc_global (transaction_id VARCHAR(26) PRIMARY KEY,xid VARCHAR(128),status VARCHAR(32),retry_count INT,started_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.outbox_event (event_id VARCHAR(64) PRIMARY KEY,aggregate_type VARCHAR(32),aggregate_id VARCHAR(26),event_type VARCHAR(64),transaction_id VARCHAR(26),producer VARCHAR(32),trace_id CHAR(32),occurred_at TIMESTAMP,payload VARCHAR(512),status VARCHAR(16))");
        jdbc.execute("CREATE TABLE business_db.manual_case (case_id VARCHAR(26) PRIMARY KEY,subject_type VARCHAR(32),subject_id VARCHAR(26),transaction_id VARCHAR(26),status VARCHAR(16),created_at TIMESTAMP,updated_at TIMESTAMP)");
        // 跨服务链路片段只读投影的测试表：账户账本、用户审计、AI 工具与审计。
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS ledger_db");
        jdbc.execute("CREATE TABLE ledger_db.outbox_event (event_id VARCHAR(64) PRIMARY KEY,aggregate_type VARCHAR(32),aggregate_id VARCHAR(26),event_type VARCHAR(64),transaction_id VARCHAR(26),producer VARCHAR(32),trace_id CHAR(32),occurred_at TIMESTAMP,payload VARCHAR(512),status VARCHAR(16))");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS user_db");
        jdbc.execute("CREATE TABLE user_db.audit_log (audit_id BIGINT PRIMARY KEY,actor_type VARCHAR(16),actor_id VARCHAR(128),action VARCHAR(64),target_type VARCHAR(32),target_id VARCHAR(128),result_code VARCHAR(32),trace_id CHAR(32),occurred_at TIMESTAMP)");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS agent_db");
        jdbc.execute("CREATE TABLE agent_db.tool_call_log (tool_call_id VARCHAR(26) PRIMARY KEY,session_id VARCHAR(26),tool_name VARCHAR(64),request_digest BINARY(32),result_code VARCHAR(32),duration_ms INT,trace_id CHAR(32),occurred_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE agent_db.audit_log (audit_id BIGINT PRIMARY KEY,actor_type VARCHAR(16),actor_id VARCHAR(128),action VARCHAR(64),target_type VARCHAR(32),target_id VARCHAR(128),result_code VARCHAR(32),trace_id CHAR(32),occurred_at TIMESTAMP)");
    }
}
