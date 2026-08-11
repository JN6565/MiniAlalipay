package com.minialalipay.account.infrastructure.ledger;

import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.ledger.LedgerReversalReason;
import com.minialalipay.account.domain.ledger.LedgerVoucher;
import com.minialalipay.account.domain.ledger.LedgerVoucherStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

/** 使用 ledger_db 实现只增不改的复式账本仓储。 */
@Repository
public class LedgerRepositoryImpl implements LedgerRepository {

    private final JdbcTemplate jdbcTemplate;

    public LedgerRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<LedgerVoucher> find(String transactionId, String voucherType, int reversalNo) {
        List<LedgerVoucher> vouchers = jdbcTemplate.query("SELECT * FROM ledger_db.ledger_voucher "
                        + "WHERE transaction_id=? AND voucher_type=? AND reversal_no=?",
                (rs, rowNum) -> mapVoucher(rs), transactionId, voucherType, reversalNo);
        return vouchers.stream().findFirst();
    }

    @Override
    public Optional<LedgerVoucher> findByIdForUpdate(String voucherId) {
        List<LedgerVoucher> vouchers = jdbcTemplate.query("SELECT * FROM ledger_db.ledger_voucher "
                        + "WHERE voucher_id=? FOR UPDATE",
                (rs, rowNum) -> mapVoucher(rs), voucherId);
        return vouchers.stream().findFirst();
    }

    @Override
    public void savePrepared(LedgerVoucher voucher) {
        jdbcTemplate.update("INSERT INTO ledger_db.ledger_voucher "
                        + "(voucher_id,transaction_id,voucher_type,reversal_no,original_voucher_id,reversal_reason,status,"
                        + "total_debit_fen,total_credit_fen,posted_at,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                voucher.getVoucherId(), voucher.getTransactionId(), voucher.getVoucherType(), voucher.getReversalNo(),
                voucher.getOriginalVoucherId(), voucher.getReversalReason() == null ? null : voucher.getReversalReason().name(),
                LedgerVoucherStatus.PREPARED.name(), voucher.getTotalDebitFen(),
                voucher.getTotalCreditFen(), null, voucher.getCreatedAt());
        for (LedgerEntry entry : voucher.getEntries()) {
            jdbcTemplate.update("INSERT INTO ledger_db.ledger_entry "
                            + "(entry_id,voucher_id,transaction_id,ledger_account_id,direction,amount_fen,sequence_no,memo,created_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?)",
                    entry.entryId(), entry.voucherId(), entry.transactionId(), entry.ledgerAccountId(),
                    entry.direction().name(), entry.amountFen(), entry.sequenceNo(), entry.memo(), entry.createdAt());
        }
    }

    @Override
    public LedgerTotals summarizeEntries(String voucherId) {
        return jdbcTemplate.queryForObject("SELECT "
                        + "COALESCE(SUM(CASE WHEN direction='DEBIT' THEN amount_fen ELSE 0 END),0) debit_fen, "
                        + "COALESCE(SUM(CASE WHEN direction='CREDIT' THEN amount_fen ELSE 0 END),0) credit_fen "
                        + "FROM ledger_db.ledger_entry WHERE voucher_id=?",
                (rs, rowNum) -> new LedgerTotals(rs.getLong("debit_fen"), rs.getLong("credit_fen")), voucherId);
    }

    @Override
    public boolean postAndAppendOutbox(LedgerVoucher voucher, String eventId, String traceId, Instant now) {
        int updated = jdbcTemplate.update("UPDATE ledger_db.ledger_voucher SET status='POSTED', posted_at=? "
                        + "WHERE voucher_id=? AND status='PREPARED'",
                now, voucher.getVoucherId());
        if (updated != 1) return false;
        // 过账成功后回填「交易后余额」展示列：以账户当前可用余额为基准，
        // 加上/减去本分录自身影响（同一凭证内同账户另一笔分录的净影响已由当前余额体现）。
        // 前提：余额参与者的 Confirm 先于账本参与者提交（分支注册顺序保证），
        // 当前余额已反映本交易。该列仅供 C 端明细展示，不参与对账与平衡校验。
        // 采用标准相关子查询而非 MySQL 专有 UPDATE JOIN，保证 H2 集成测试同构。
        jdbcTemplate.update("UPDATE ledger_db.ledger_entry e "
                        + "SET e.balance_after_fen = ("
                        + "  SELECT ab.available_fen "
                        + "    + CASE WHEN e.direction='DEBIT' THEN e.amount_fen ELSE -e.amount_fen END "
                        + "  FROM ledger_db.ledger_account la "
                        + "  JOIN account_db.account a ON a.user_id=la.owner_id "
                        + "    AND a.account_type='PERSONAL' AND a.currency='CNY' "
                        + "  JOIN account_db.account_balance ab ON ab.account_id=a.account_id "
                        + "  WHERE la.ledger_account_id=e.ledger_account_id AND la.owner_type='USER' LIMIT 1) "
                        + "WHERE e.voucher_id=? AND EXISTS ("
                        + "  SELECT 1 FROM ledger_db.ledger_account la2 "
                        + "  WHERE la2.ledger_account_id=e.ledger_account_id AND la2.owner_type='USER')",
                voucher.getVoucherId());
        String payload = "{\"voucherId\":\"" + voucher.getVoucherId()
                + "\",\"transactionId\":\"" + voucher.getTransactionId() + "\"}";
        jdbcTemplate.update("INSERT INTO ledger_db.outbox_event "
                        + "(event_id,aggregate_type,aggregate_id,aggregate_version,event_type,event_version,"
                        + "business_type,transaction_id,producer,trace_id,occurred_at,payload,status,retry_count,created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                eventId, "LEDGER_VOUCHER", voucher.getVoucherId(), 1L, "ledger.voucher.posted", 1,
                voucher.getVoucherType(), voucher.getTransactionId(), "account-center", traceId, now,
                payload, "PENDING", 0, now);
        return true;
    }

    @Override
    public boolean cancelPrepared(String voucherId) {
        return jdbcTemplate.update("UPDATE ledger_db.ledger_voucher SET status='CANCELLED' WHERE voucher_id=? AND status='PREPARED'", voucherId) == 1;
    }

    @Override
    public boolean isPostedAndBalanced(String transactionId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_db.ledger_voucher v WHERE v.transaction_id=? AND v.reversal_no=0 AND v.status='POSTED' AND v.total_debit_fen=v.total_credit_fen AND v.total_debit_fen=(SELECT COALESCE(SUM(CASE WHEN e.direction='DEBIT' THEN e.amount_fen ELSE 0 END),0) FROM ledger_db.ledger_entry e WHERE e.voucher_id=v.voucher_id) AND v.total_credit_fen=(SELECT COALESCE(SUM(CASE WHEN e.direction='CREDIT' THEN e.amount_fen ELSE 0 END),0) FROM ledger_db.ledger_entry e WHERE e.voucher_id=v.voucher_id)", Integer.class, transactionId);
        return count != null && count == 1;
    }

    @Override
    public List<LedgerEntry> findEntriesByUserId(String userId, Instant cursorCreatedAt,
                                                  long cursorEntryId, int limit) {
        return jdbcTemplate.query("SELECT e.* FROM ledger_db.ledger_entry e "
                        + "JOIN ledger_db.ledger_account a ON a.ledger_account_id=e.ledger_account_id "
                        + "WHERE a.owner_type='USER' AND a.owner_id=? "
                        + "AND (? IS NULL OR e.created_at<? OR (e.created_at=? AND e.entry_id<?)) "
                        + "ORDER BY e.created_at DESC, e.entry_id DESC LIMIT ?",
                this::mapEntry, userId, cursorCreatedAt, cursorCreatedAt, cursorCreatedAt, cursorEntryId, limit);
    }

    @Override
    public List<LedgerEntry> findPostedEntriesByUserId(String userId, Instant since, Instant until) {
        return jdbcTemplate.query("SELECT e.* FROM ledger_db.ledger_entry e "
                        + "JOIN ledger_db.ledger_account a ON a.ledger_account_id=e.ledger_account_id "
                        + "JOIN ledger_db.ledger_voucher v ON v.voucher_id=e.voucher_id "
                        + "WHERE a.owner_type='USER' AND a.owner_id=? AND v.status='POSTED' "
                        + "AND e.created_at>=? AND e.created_at<=? ORDER BY e.created_at",
                this::mapEntry, userId, since, until);
    }

    @Override
    public List<LedgerEntry.WithCounterparty> findEntriesWithCounterparty(String userId,
                                                                           Instant cursorCreatedAt,
                                                                           long cursorEntryId, int limit) {
        return jdbcTemplate.query(
                "SELECT e.*, cp_a.owner_id AS counterparty_user_id "
                        + "FROM ledger_db.ledger_entry e "
                        + "JOIN ledger_db.ledger_account a ON a.ledger_account_id=e.ledger_account_id "
                        + "LEFT JOIN ledger_db.ledger_entry cp_e "
                        + "  ON cp_e.transaction_id=e.transaction_id "
                        + "  AND cp_e.entry_id<>e.entry_id "
                        + "LEFT JOIN ledger_db.ledger_account cp_a "
                        + "  ON cp_a.ledger_account_id=cp_e.ledger_account_id "
                        + "  AND cp_a.owner_type='USER' "
                        + "WHERE a.owner_type='USER' AND a.owner_id=? "
                        + "AND (? IS NULL OR e.created_at<? OR (e.created_at=? AND e.entry_id<?)) "
                        + "ORDER BY e.created_at DESC, e.entry_id DESC LIMIT ?",
                this::mapEntryWithCounterparty, userId, cursorCreatedAt, cursorCreatedAt,
                cursorCreatedAt, cursorEntryId, limit);
    }

    private LedgerVoucher mapVoucher(ResultSet rs) throws SQLException {
        String voucherId = rs.getString("voucher_id");
        List<LedgerEntry> entries = jdbcTemplate.query("SELECT * FROM ledger_db.ledger_entry "
                + "WHERE voucher_id=? ORDER BY sequence_no", this::mapEntry, voucherId);
        var postedTimestamp = rs.getTimestamp("posted_at");
        String reversalReason = rs.getString("reversal_reason");
        return new LedgerVoucher(voucherId, rs.getString("transaction_id"), rs.getString("voucher_type"),
                rs.getInt("reversal_no"), rs.getString("original_voucher_id"),
                reversalReason == null ? null : LedgerReversalReason.valueOf(reversalReason),
                LedgerVoucherStatus.valueOf(rs.getString("status")), rs.getLong("total_debit_fen"),
                rs.getLong("total_credit_fen"), entries,
                postedTimestamp == null ? null : postedTimestamp.toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }

    private LedgerEntry mapEntry(ResultSet rs, int rowNum) throws SQLException {
        return new LedgerEntry(rs.getLong("entry_id"), rs.getString("voucher_id"),
                rs.getString("transaction_id"), rs.getString("ledger_account_id"),
                LedgerDirection.valueOf(rs.getString("direction")), rs.getLong("amount_fen"),
                rs.getInt("sequence_no"), rs.getString("memo"), rs.getTimestamp("created_at").toInstant());
    }

    private LedgerEntry.WithCounterparty mapEntryWithCounterparty(ResultSet rs, int rowNum) throws SQLException {
        LedgerEntry entry = mapEntry(rs, rowNum);
        String counterpartyUserId = rs.getString("counterparty_user_id");
        long balanceAfter = rs.getLong("balance_after_fen");
        Long balanceAfterFen = rs.wasNull() ? null : balanceAfter;
        return new LedgerEntry.WithCounterparty(entry, counterpartyUserId, balanceAfterFen);
    }
}
