package com.minialalipay.account.infrastructure.reconciliation;

import com.minialalipay.account.domain.reconciliation.ReconciliationDiffRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneId;

/** 使用账本 Schema 持久化不可覆盖的对账差异证据。 */
@Repository
public class JdbcReconciliationDiffRepository implements ReconciliationDiffRepository {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbcTemplate;

    public JdbcReconciliationDiffRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void append(String diffId, String transactionId, String diffType, String expectedJson,
                       String actualJson, String manualCaseId, String traceId, Instant detectedAt) {
        jdbcTemplate.update("INSERT INTO ledger_db.reconciliation_diff "
                        + "(diff_id,biz_date,transaction_id,diff_type,expected_json,actual_json,status,manual_case_id,trace_id,created_at) "
                        + "VALUES (?,?,?,?,?,?,'OPEN',?,?,?) "
                        + "ON DUPLICATE KEY UPDATE manual_case_id=COALESCE(manual_case_id,VALUES(manual_case_id))",
                diffId, detectedAt.atZone(BUSINESS_ZONE).toLocalDate(), transactionId, diffType,
                expectedJson, actualJson, manualCaseId, traceId, detectedAt);
    }
}
