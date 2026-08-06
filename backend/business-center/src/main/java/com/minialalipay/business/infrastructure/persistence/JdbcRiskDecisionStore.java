package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.RiskDecisionStore;
import com.minialalipay.business.domain.risk.RiskDecision;
import com.minialalipay.business.domain.risk.RiskDecisionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/** business_db.risk_decision 的 JDBC 仓储实现。 */
@Repository
public class JdbcRiskDecisionStore implements RiskDecisionStore {
    private final JdbcTemplate jdbc;

    /** 创建风控决策仓储。 */
    public JdbcRiskDecisionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RiskDecision> findLatestBySubject(String subjectType, String subjectId) {
        return jdbc.query("SELECT decision_id,subject_type,subject_id,transaction_id,rule_version,risk_level,action,reason_code,created_at "
                        + "FROM business_db.risk_decision WHERE subject_type=? AND subject_id=? "
                        + "ORDER BY created_at DESC,decision_id DESC LIMIT 1",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), subjectType, subjectId);
    }

    @Override
    public boolean save(RiskDecision decision) {
        return jdbc.update("INSERT INTO business_db.risk_decision "
                        + "(decision_id,subject_type,subject_id,transaction_id,rule_version,risk_level,action,reason_code,created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                decision.getDecisionId(), decision.getSubjectType(), decision.getSubjectId(),
                decision.getTransactionId(), decision.getRuleVersion(), decision.getRiskLevel(),
                decision.getStatus().name(), decision.getReasonCode(),
                Timestamp.from(decision.getCreatedAt())) == 1;
    }

    private RiskDecision map(ResultSet rs) throws SQLException {
        return new RiskDecision(rs.getString("decision_id"), rs.getString("subject_type"),
                rs.getString("subject_id"), rs.getString("transaction_id"), rs.getString("rule_version"),
                rs.getString("risk_level"), RiskDecisionStatus.valueOf(rs.getString("action")),
                rs.getString("reason_code"), rs.getTimestamp("created_at").toInstant());
    }
}
