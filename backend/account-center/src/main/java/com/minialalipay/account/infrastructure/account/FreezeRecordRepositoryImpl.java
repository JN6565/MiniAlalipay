package com.minialalipay.account.infrastructure.account;

import com.minialalipay.account.domain.account.FreezePurpose;
import com.minialalipay.account.domain.account.FreezeRecord;
import com.minialalipay.account.domain.account.FreezeRecordRepository;
import com.minialalipay.account.domain.account.FreezeStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** 使用 account_db.freeze_record 实现余额冻结事实的唯一键查询和状态 CAS。 */
@Repository
public class FreezeRecordRepositoryImpl implements FreezeRecordRepository {

    private final JdbcTemplate jdbcTemplate;

    public FreezeRecordRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<FreezeRecord> find(String transactionId, String accountId, FreezePurpose purpose) {
        return jdbcTemplate.query("SELECT * FROM account_db.freeze_record "
                        + "WHERE transaction_id=? AND account_id=? AND purpose=?",
                this::extractFirst, transactionId, accountId, purpose.name());
    }

    @Override
    public Optional<FreezeRecord> findForUpdate(String transactionId, String accountId, FreezePurpose purpose) {
        return jdbcTemplate.query("SELECT * FROM account_db.freeze_record "
                        + "WHERE transaction_id=? AND account_id=? AND purpose=? FOR UPDATE",
                this::extractFirst, transactionId, accountId, purpose.name());
    }

    @Override
    public void create(FreezeRecord record) {
        jdbcTemplate.update("INSERT INTO account_db.freeze_record "
                        + "(freeze_id,transaction_id,account_id,purpose,amount_fen,status,branch_xid,version,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                record.getFreezeId(), record.getTransactionId(), record.getAccountId(), record.getPurpose().name(),
                record.getAmountFen(), record.getStatus().name(), record.getBranchXid(), record.getVersion(),
                record.getCreatedAt(), record.getUpdatedAt());
    }

    @Override
    public boolean update(FreezeRecord record, long expectedVersion) {
        int updated = jdbcTemplate.update("UPDATE account_db.freeze_record SET status=?, version=version+1, "
                        + "updated_at=? WHERE freeze_id=? AND version=?",
                record.getStatus().name(), record.getUpdatedAt(), record.getFreezeId(), expectedVersion);
        if (updated == 1) record.updateVersion(expectedVersion + 1);
        return updated == 1;
    }

    private Optional<FreezeRecord> extractFirst(ResultSet rs) throws SQLException {
        if (!rs.next()) return Optional.empty();
        return Optional.of(new FreezeRecord(rs.getString("freeze_id"), rs.getString("transaction_id"),
                rs.getString("account_id"), FreezePurpose.valueOf(rs.getString("purpose")),
                rs.getLong("amount_fen"), rs.getString("branch_xid"),
                FreezeStatus.valueOf(rs.getString("status")), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()));
    }
}
