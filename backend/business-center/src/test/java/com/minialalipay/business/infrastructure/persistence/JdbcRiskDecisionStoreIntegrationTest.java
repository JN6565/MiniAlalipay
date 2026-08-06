package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.domain.risk.RiskDecision;
import com.minialalipay.business.domain.risk.RiskDecisionStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 前置风控决策仓储的 H2 集成测试。 */
class JdbcRiskDecisionStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    private JdbcRiskDecisionStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:risk_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS business_db");
        jdbc.execute("CREATE TABLE business_db.risk_decision (decision_id VARCHAR(26) PRIMARY KEY,subject_type VARCHAR(24),"
                + "subject_id VARCHAR(26),transaction_id VARCHAR(26),rule_version VARCHAR(32),risk_level VARCHAR(16),"
                + "action VARCHAR(16),reason_code VARCHAR(32),created_at TIMESTAMP)");
        store = new JdbcRiskDecisionStore(jdbc);
    }

    @Test
    void 保存并读取按主体最新的决策() {
        RiskDecision manual = RiskDecision.manualReview("d-1", "QR_PAY_ORDER", "order-1", null, "v1", "MEDIUM", NOW);
        assertThat(store.save(manual)).isTrue();

        RiskDecision loaded = store.findLatestBySubject("QR_PAY_ORDER", "order-1").orElseThrow();
        assertThat(loaded.getDecisionId()).isEqualTo("d-1");
        assertThat(loaded.getStatus()).isEqualTo(RiskDecisionStatus.MANUAL_REVIEW);
        assertThat(loaded.getRiskLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void 存在多条决策时返回最新一条() {
        store.save(RiskDecision.pass("d-1", "QR_PAY_ORDER", "order-1", "v1", "LOW", NOW));
        store.save(RiskDecision.reject("d-2", "QR_PAY_ORDER", "order-1", "v1", "HIGH", "RISK_REJECTED", NOW.plusSeconds(5)));

        RiskDecision loaded = store.findLatestBySubject("QR_PAY_ORDER", "order-1").orElseThrow();
        assertThat(loaded.getDecisionId()).isEqualTo("d-2");
        assertThat(loaded.getStatus()).isEqualTo(RiskDecisionStatus.REJECT);
    }

    @Test
    void 无决策时返回空() {
        assertThat(store.findLatestBySubject("QR_PAY_ORDER", "order-missing")).isEmpty();
    }
}
