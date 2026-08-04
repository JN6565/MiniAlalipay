package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.domain.confirmation.Confirmation;
import com.minialalipay.business.domain.confirmation.ConfirmationStatus;
import com.minialalipay.business.domain.confirmation.SubjectType;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionStatus;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.business.domain.transfer.DraftStatus;
import com.minialalipay.business.domain.transfer.TransferDraft;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 使用 business_db 实现草稿、确认、统一交易、TCC 全局和 Outbox 本地事实。 */
@Repository
public class JdbcBusinessStore implements BusinessStore {
    private final JdbcTemplate jdbc;
    public JdbcBusinessStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean reserveIdempotency(String recordId, String principalId, String operation, String key,
                                      byte[] requestHash, String resourceType, String resourceId, Instant now) {
        return jdbc.update("INSERT IGNORE INTO business_db.idempotency_record "
                        + "(record_id,principal_key,api_scope,idempotency_key,request_digest,resource_type,resource_id,status,expires_at,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,'PROCESSING',?,?,?)",
                recordId, principalId, operation, key, requestHash, resourceType, resourceId,
                now.plusSeconds(86400), now, now) == 1;
    }

    @Override
    public void createDraft(TransferDraft d) {
        jdbc.update("INSERT INTO business_db.transfer_draft (draft_id,payer_user_id,payee_user_id,payer_account_id,payee_account_id,amount_fen,remark,status,version,expires_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                d.getDraftId(), d.getPayerUserId(), d.getPayeeUserId(), d.getPayerAccountId(), d.getPayeeAccountId(),
                d.getAmountFen(), d.getRemark(), d.getStatus().name(), d.getVersion(), d.getExpiresAt(), d.getCreatedAt(), d.getUpdatedAt());
        jdbc.update("UPDATE business_db.idempotency_record SET status='COMPLETED',updated_at=? WHERE resource_type='TRANSFER_DRAFT' AND resource_id=?",
                d.getUpdatedAt(), d.getDraftId());
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String key) {
        return jdbc.query("SELECT request_digest,resource_id FROM business_db.idempotency_record WHERE principal_key=? AND api_scope=? AND idempotency_key=?",
                rs -> rs.next() ? Optional.of(new IdempotencyRecord(rs.getBytes(1), rs.getString(2))) : Optional.empty(),
                principal, operation, key);
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotencyForUpdate(String principal, String operation, String key) {
        return jdbc.query("SELECT request_digest,resource_id FROM business_db.idempotency_record WHERE principal_key=? AND api_scope=? AND idempotency_key=? FOR UPDATE",
                rs -> rs.next() ? Optional.of(new IdempotencyRecord(rs.getBytes(1), rs.getString(2))) : Optional.empty(),
                principal, operation, key);
    }

    @Override public Optional<TransferDraft> findDraft(String id) {
        return jdbc.query("SELECT * FROM business_db.transfer_draft WHERE draft_id=?", this::firstDraft, id);
    }

    @Override public boolean updateDraft(TransferDraft d, long expected) {
        return jdbc.update("UPDATE business_db.transfer_draft SET amount_fen=?,remark=?,status=?,version=?,updated_at=? WHERE draft_id=? AND version=?",
                d.getAmountFen(), d.getRemark(), d.getStatus().name(), d.getVersion(), d.getUpdatedAt(), d.getDraftId(), expected) == 1;
    }

    @Override
    public void replaceConfirmation(Confirmation c, long draftExpectedVersion, TransferDraft draft) {
        jdbc.update("UPDATE business_db.confirmation SET status='REVOKED' WHERE subject_type=? AND subject_id=? AND status='ACTIVE'",
                c.getSubjectType().name(), c.getSubjectId());
        jdbc.update("INSERT INTO business_db.confirmation (confirmation_id,token_digest,subject_type,subject_id,subject_hash,payer_user_id,payment_proof_id,pay_password_version,status,expires_at,consumed_at,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                c.getConfirmationId(), c.getTokenDigest(), c.getSubjectType().name(), c.getSubjectId(), c.getSubjectHash(),
                c.getPayerUserId(), c.getPaymentProofId(), c.getPayPasswordVersion(), c.getStatus().name(),
                c.getExpiresAt(), c.getConsumedAt(), c.getCreatedAt());
        jdbc.update("INSERT INTO business_db.confirmation_subject (subject_type,subject_id,current_confirmation_id,version,updated_at) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE current_confirmation_id=VALUES(current_confirmation_id),version=version+1,updated_at=VALUES(updated_at)",
                c.getSubjectType().name(), c.getSubjectId(), c.getConfirmationId(), 0L, c.getCreatedAt());
        if (!updateDraft(draft, draftExpectedVersion)) throw new IllegalStateException("资源版本已经变化");
    }

    @Override public Optional<Confirmation> findConfirmationForUpdate(byte[] digest) {
        return jdbc.query("SELECT * FROM business_db.confirmation WHERE token_digest=? FOR UPDATE", this::firstConfirmation, digest);
    }

    @Override public boolean updateConfirmation(Confirmation c, String expectedStatus) {
        return jdbc.update("UPDATE business_db.confirmation SET status=?,consumed_at=? WHERE confirmation_id=? AND status=?",
                c.getStatus().name(), c.getConsumedAt(), c.getConfirmationId(), expectedStatus) == 1;
    }

    @Override public Optional<FundTransactionRecord> findByIdempotency(String userId, TransactionType type, String key) {
        return jdbc.query("SELECT t.*,i.request_digest AS request_hash FROM business_db.fund_transaction t JOIN business_db.idempotency_record i ON i.resource_type='FUND_TRANSACTION' AND i.resource_id=t.transaction_id WHERE t.initiator_user_id=? AND t.business_type=? AND t.idempotency_key=? FOR UPDATE",
                this::firstTransaction, userId, type.name(), key);
    }
    @Override public Optional<FundTransactionRecord> findBySource(String sourceType, String sourceId) {
        return jdbc.query("SELECT t.*,NULL AS request_hash FROM business_db.fund_transaction t WHERE source_type=? AND source_order_id=? FOR UPDATE",
                this::firstTransaction, sourceType, sourceId);
    }
    @Override public Optional<FundTransactionRecord> findTransaction(String transactionId) {
        return jdbc.query("SELECT t.*,NULL AS request_hash FROM business_db.fund_transaction t WHERE transaction_id=?", this::firstTransaction, transactionId);
    }

    @Override
    public void createTransaction(FundTransaction t, byte[] requestHash, String eventId, Instant now) {
        jdbc.update("INSERT INTO business_db.fund_transaction (transaction_id,business_type,source_type,source_order_id,initiator_user_id,payer_account_id,payee_account_id,funding_source,amount_fen,idempotency_key,status,risk_level,trace_id,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                t.getTransactionId(), t.getBusinessType().name(), t.getSourceType().name(), t.getSourceOrderId(),
                t.getInitiatorUserId(), t.getPayerAccountId(), t.getPayeeAccountId(), t.getFundingSource().name(),
                t.getAmountFen(), t.getIdempotencyKey(), t.getStatus().name(), t.getRiskLevel(),
                t.getTraceId(), t.getVersion(), t.getCreatedAt(), t.getUpdatedAt());
        jdbc.update("INSERT INTO business_db.idempotency_record (record_id,principal_key,api_scope,idempotency_key,request_digest,resource_type,resource_id,status,expires_at,created_at,updated_at) VALUES (?,?,?,?,?,'FUND_TRANSACTION',?,'COMPLETED',?,?,?)",
                eventId, t.getInitiatorUserId(), "SUBMIT_TRANSFER", t.getIdempotencyKey(), requestHash,
                t.getTransactionId(), now.plusSeconds(86400), now, now);
        appendOutbox(t, eventId, "transaction.accepted", now);
    }

    @Override
    @Transactional
    public boolean updateTransaction(FundTransaction t, long expected, String eventId, Instant now) {
        int changed = jdbc.update("UPDATE business_db.fund_transaction SET status=?,version=?,updated_at=? WHERE transaction_id=? AND version=?",
                t.getStatus().name(), t.getVersion(), t.getUpdatedAt(), t.getTransactionId(), expected);
        if (changed == 1) appendOutbox(t, eventId, "transaction.status.changed", now);
        return changed == 1;
    }

    @Override public void createTccGlobal(String xid, String transactionId, Instant now) {
        jdbc.update("INSERT IGNORE INTO business_db.tcc_global (transaction_id,xid,status,started_at,updated_at) VALUES (?,?,'PROCESSING',?,?)",
                transactionId, xid, now, now);
    }
    @Override public void updateTccGlobal(String xid, String status, String summary, Instant nextRetryAt, Instant now) {
        jdbc.update("UPDATE business_db.tcc_global SET status=?,retry_count=retry_count+1,next_retry_at=?,updated_at=? WHERE xid=?",
                status, nextRetryAt, now, xid);
    }

    @Override
    @Transactional
    public boolean finalizeTransaction(FundTransaction transaction, long expectedVersion, String xid,
                                       String globalStatus, String eventId, Instant now) {
        int changed = jdbc.update("UPDATE business_db.fund_transaction SET status=?,version=?,updated_at=? WHERE transaction_id=? AND version=?",
                transaction.getStatus().name(), transaction.getVersion(), transaction.getUpdatedAt(),
                transaction.getTransactionId(), expectedVersion);
        if (changed != 1) return false;
        appendOutbox(transaction, eventId, "transaction.status.changed", now);
        jdbc.update("UPDATE business_db.tcc_global SET status=?,next_retry_at=NULL,updated_at=? WHERE xid=?",
                globalStatus, now, xid);
        return true;
    }

    @Override
    @Transactional
    public boolean moveToManualReview(FundTransaction transaction, long expectedVersion, String xid,
                                      String eventId, String caseId, String reasonCode, Instant now) {
        int changed = jdbc.update("UPDATE business_db.fund_transaction SET status=?,version=?,updated_at=? WHERE transaction_id=? AND version=?",
                transaction.getStatus().name(), transaction.getVersion(), transaction.getUpdatedAt(),
                transaction.getTransactionId(), expectedVersion);
        if (changed != 1) return false;
        appendOutbox(transaction, eventId, "transaction.status.changed", now);
        jdbc.update("UPDATE business_db.tcc_global SET status='MANUAL_REVIEW',next_retry_at=NULL,updated_at=? WHERE xid=?",
                now, xid);
        jdbc.update("INSERT INTO business_db.manual_case (case_id,case_type,subject_type,subject_id,transaction_id,reason_code,status,created_at,updated_at) "
                        + "VALUES (?,'TRANSACTION_RECOVERY','FUND_TRANSACTION',?,?,?,'OPEN',?,?) "
                        + "ON DUPLICATE KEY UPDATE updated_at=VALUES(updated_at)",
                caseId, transaction.getTransactionId(), transaction.getTransactionId(), reasonCode, now, now);
        return true;
    }
    @Override public List<FundTransactionRecord> findRecoverable(Instant before, int limit) {
        return jdbc.query("SELECT t.*,NULL AS request_hash FROM business_db.fund_transaction t WHERE status IN ('PROCESSING','COMPENSATING') AND updated_at<? ORDER BY updated_at LIMIT ?",
                (rs, n) -> transactionRecord(rs), before, limit);
    }

    private void appendOutbox(FundTransaction t, String eventId, String type, Instant now) {
        String payload = "{\"transactionId\":\"" + t.getTransactionId() + "\",\"status\":\"" + t.getStatus().name() + "\"}";
        jdbc.update("INSERT INTO business_db.outbox_event (event_id,aggregate_type,aggregate_id,aggregate_version,event_type,event_version,transaction_id,producer,trace_id,occurred_at,payload,created_at) VALUES (?,'FUND_TRANSACTION',?,?,?,1,?,'business-center',?,?,?,?)",
                eventId, t.getTransactionId(), t.getVersion(), type, t.getTransactionId(), t.getTraceId(), now, payload, now);
    }
    private Optional<TransferDraft> firstDraft(ResultSet rs) throws SQLException { return rs.next() ? Optional.of(draft(rs)) : Optional.empty(); }
    private Optional<Confirmation> firstConfirmation(ResultSet rs) throws SQLException { return rs.next() ? Optional.of(confirmation(rs)) : Optional.empty(); }
    private Optional<FundTransactionRecord> firstTransaction(ResultSet rs) throws SQLException { return rs.next() ? Optional.of(transactionRecord(rs)) : Optional.empty(); }
    private TransferDraft draft(ResultSet r) throws SQLException {
        return new TransferDraft(r.getString("draft_id"), r.getString("payer_user_id"), r.getString("payee_user_id"),
                r.getString("payer_account_id"), r.getString("payee_account_id"), r.getLong("amount_fen"), r.getString("remark"),
                DraftStatus.valueOf(r.getString("status")), r.getLong("version"), instant(r,"expires_at"), instant(r,"created_at"), instant(r,"updated_at"));
    }
    private Confirmation confirmation(ResultSet r) throws SQLException {
        var consumed = r.getTimestamp("consumed_at");
        return new Confirmation(r.getString("confirmation_id"), r.getBytes("token_digest"), SubjectType.valueOf(r.getString("subject_type")),
                r.getString("subject_id"), r.getBytes("subject_hash"), r.getString("payer_user_id"), r.getString("payment_proof_id"),
                r.getLong("pay_password_version"), ConfirmationStatus.valueOf(r.getString("status")), instant(r,"expires_at"),
                consumed == null ? null : consumed.toInstant(), instant(r,"created_at"));
    }
    private FundTransactionRecord transactionRecord(ResultSet r) throws SQLException {
        FundTransaction t = new FundTransaction(r.getString("transaction_id"), TransactionType.valueOf(r.getString("business_type")),
                SourceType.valueOf(r.getString("source_type")), r.getString("source_order_id"), r.getString("initiator_user_id"),
                r.getString("payer_account_id"), r.getString("payee_account_id"), FundingSource.valueOf(r.getString("funding_source")),
                r.getLong("amount_fen"), r.getString("idempotency_key"), TransactionStatus.valueOf(r.getString("status")),
                r.getString("risk_level"), r.getString("trace_id"), r.getLong("version"), instant(r,"created_at"), instant(r,"updated_at"));
        return new FundTransactionRecord(t, r.getBytes("request_hash"));
    }
    private static Instant instant(ResultSet r, String column) throws SQLException { return r.getTimestamp(column).toInstant(); }
}
