package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.ManualCaseStore;
import com.minialalipay.business.domain.manualcase.ManualCase;
import com.minialalipay.business.domain.manualcase.ManualCaseStatus;
import com.minialalipay.business.domain.manualcase.ManualCaseType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** business_db.manual_case 的 JDBC 仓储实现。 */
@Repository
public class JdbcManualCaseStore implements ManualCaseStore {
    private final JdbcTemplate jdbc;

    /** 创建工单仓储。 */
    public JdbcManualCaseStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ManualCase> list(String cursor, int limit) {
        String afterId = cursor == null || cursor.isBlank() ? "" : cursor;
        return jdbc.query("SELECT case_id,case_type,subject_type,subject_id,reason_code,status,operator_id,last_reason,evidence_reference,version,created_at,updated_at "
                        + "FROM business_db.manual_case WHERE case_id>? ORDER BY case_id ASC LIMIT ?",
                (rs, rowNum) -> map(rs), afterId, limit);
    }

    @Override
    public boolean create(ManualCase manualCase) {
        return jdbc.update("INSERT IGNORE INTO business_db.manual_case "
                        + "(case_id,case_type,subject_type,subject_id,reason_code,status,version,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                manualCase.getCaseId(), manualCase.getType().name(), manualCase.getSubjectType(),
                manualCase.getSubjectId(), manualCase.getReasonCode(), manualCase.getStatus().name(),
                manualCase.getVersion(), Timestamp.from(manualCase.getCreatedAt()),
                Timestamp.from(manualCase.getUpdatedAt())) == 1;
    }

    @Override
    public Optional<ManualCase> find(String caseId) {
        return jdbc.query("SELECT case_id,case_type,subject_type,subject_id,reason_code,status,operator_id,last_reason,evidence_reference,version,created_at,updated_at "
                        + "FROM business_db.manual_case WHERE case_id=?",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), caseId);
    }

    @Override
    public boolean update(ManualCase manualCase, long expectedVersion) {
        return jdbc.update("UPDATE business_db.manual_case SET status=?,operator_id=?,last_reason=?,evidence_reference=?,version=?,updated_at=? "
                        + "WHERE case_id=? AND version=?",
                manualCase.getStatus().name(), manualCase.getOperatorId(), manualCase.getLastReason(),
                manualCase.getEvidenceReference(), manualCase.getVersion(), Timestamp.from(manualCase.getUpdatedAt()),
                manualCase.getCaseId(), expectedVersion) == 1;
    }

    @Override
    public Optional<DecisionIdempotencyRecord> findDecisionIdempotency(String operatorId, String idempotencyKey) {
        return jdbc.query("SELECT request_digest,case_id,case_type,subject_type,subject_id,reason_code,status,"
                        + "case_operator_id,last_reason,evidence_reference,case_version,case_created_at,case_updated_at "
                        + "FROM business_db.manual_case_decision_idempotency WHERE operator_id=? AND idempotency_key=?",
                rs -> rs.next() ? Optional.of(new DecisionIdempotencyRecord(rs.getBytes("request_digest"),
                        rs.getString("case_id") == null ? null : new ManualCase(rs.getString("case_id"),
                                ManualCaseType.valueOf(rs.getString("case_type")), rs.getString("subject_type"),
                                rs.getString("subject_id"), rs.getString("reason_code"),
                                ManualCaseStatus.valueOf(rs.getString("status")), rs.getString("case_operator_id"),
                                rs.getString("last_reason"), rs.getString("evidence_reference"), rs.getLong("case_version"),
                                rs.getTimestamp("case_created_at").toInstant(), rs.getTimestamp("case_updated_at").toInstant())))
                        : Optional.empty(), operatorId, idempotencyKey);
    }

    @Override
    public boolean reserveDecisionIdempotency(String recordId, String operatorId, String idempotencyKey, byte[] requestHash) {
        return jdbc.update("INSERT IGNORE INTO business_db.manual_case_decision_idempotency "
                        + "(record_id,operator_id,idempotency_key,request_digest,created_at,updated_at) "
                        + "VALUES (?,?,?,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                recordId, operatorId, idempotencyKey, requestHash) == 1;
    }

    @Override
    public void completeDecisionIdempotency(String operatorId, String idempotencyKey, ManualCase manualCase) {
        jdbc.update("UPDATE business_db.manual_case_decision_idempotency SET case_id=?,case_type=?,subject_type=?,subject_id=?,"
                        + "reason_code=?,status=?,case_operator_id=?,last_reason=?,evidence_reference=?,case_version=?,"
                        + "case_created_at=?,case_updated_at=?,updated_at=UTC_TIMESTAMP(3) WHERE operator_id=? AND idempotency_key=?",
                manualCase.getCaseId(), manualCase.getType().name(), manualCase.getSubjectType(), manualCase.getSubjectId(),
                manualCase.getReasonCode(), manualCase.getStatus().name(), manualCase.getOperatorId(), manualCase.getLastReason(),
                manualCase.getEvidenceReference(), manualCase.getVersion(), Timestamp.from(manualCase.getCreatedAt()),
                Timestamp.from(manualCase.getUpdatedAt()), operatorId, idempotencyKey);
    }

    private ManualCase map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ManualCase(rs.getString("case_id"), ManualCaseType.valueOf(rs.getString("case_type")),
                rs.getString("subject_type"), rs.getString("subject_id"), rs.getString("reason_code"),
                ManualCaseStatus.valueOf(rs.getString("status")), rs.getString("operator_id"),
                rs.getString("last_reason"), rs.getString("evidence_reference"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
