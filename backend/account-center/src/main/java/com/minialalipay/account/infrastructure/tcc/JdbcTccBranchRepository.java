package com.minialalipay.account.infrastructure.tcc;

import com.minialalipay.account.domain.tcc.RollbackType;
import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** 使用 account_db.tcc_branch 持久化账户参与者屏障。 */
@Repository
public class JdbcTccBranchRepository implements TccBranchRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcTccBranchRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Override
    public Optional<TccBranch> findAccountBranchForUpdate(String xid, TccBranchType type, String resourceId) {
        return jdbcTemplate.query("SELECT * FROM account_db.tcc_branch WHERE xid=? AND branch_type=? AND resource_id=? FOR UPDATE",
                this::first, xid, type.name(), resourceId);
    }

    @Override
    public void createAccountBranch(TccBranch branch) {
        jdbcTemplate.update("INSERT INTO account_db.tcc_branch (branch_id,xid,branch_type,resource_id,transaction_id,amount_fen,status,rollback_type,barrier_version,created_at,updated_at) "
                        + "VALUES (SUBSTRING(SHA2(CONCAT(?,':',?,':',?),256),1,26),?,?,?,?,?,?,?,?,?,?)",
                branch.getXid(), branch.getBranchType().name(), branch.getResourceId(),
                branch.getXid(), branch.getBranchType().name(), branch.getResourceId(), branch.getTransactionId(),
                branch.getAmountFen(), branch.getStatus().name(), name(branch.getRollbackType()),
                branch.getBarrierVersion(), branch.getCreatedAt(), branch.getUpdatedAt());
    }

    @Override
    public boolean updateAccountBranch(TccBranch branch, long expectedVersion) {
        return jdbcTemplate.update("UPDATE account_db.tcc_branch SET status=?,rollback_type=?,barrier_version=?,updated_at=? WHERE xid=? AND branch_type=? AND resource_id=? AND barrier_version=?",
                branch.getStatus().name(), name(branch.getRollbackType()), branch.getBarrierVersion(), branch.getUpdatedAt(),
                branch.getXid(), branch.getBranchType().name(), branch.getResourceId(), expectedVersion) == 1;
    }

    @Override
    public boolean allAccountBranches(String transactionId, TccBranchStatus status, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_db.tcc_branch WHERE transaction_id=? AND status=?",
                Integer.class, transactionId, status.name());
        return count != null && count == expectedCount;
    }

    @Override
    public boolean hasAccountBranch(String transactionId, TccBranchType branchType) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account_db.tcc_branch WHERE transaction_id=? AND branch_type=?",
                Integer.class, transactionId, branchType.name());
        return count != null && count > 0;
    }

    @Override public Optional<TccBranch> findLedgerBranchForUpdate(String xid, String resourceId) {
        return findLedgerBranchForUpdate(xid, TccBranchType.LEDGER, resourceId);
    }
    @Override public Optional<TccBranch> findLedgerBranchForUpdate(String xid, TccBranchType branchType, String resourceId) {
        return jdbcTemplate.query("SELECT * FROM ledger_db.tcc_branch WHERE xid=? AND branch_type=? AND resource_id=? FOR UPDATE",
                this::first, xid, branchType.name(), resourceId);
    }
    @Override public void createLedgerBranch(TccBranch branch) {
        insert("ledger_db.tcc_branch", branch);
    }
    @Override public boolean updateLedgerBranch(TccBranch branch, long expectedVersion) {
        return update("ledger_db.tcc_branch", branch, expectedVersion);
    }
    @Override public boolean ledgerBranchIs(String transactionId, TccBranchStatus status) {
        return ledgerBranchIs(transactionId, TccBranchType.LEDGER, status);
    }
    @Override public boolean ledgerBranchIs(String transactionId, TccBranchType branchType, TccBranchStatus status) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_db.tcc_branch WHERE transaction_id=? AND branch_type=? AND status=?",
                Integer.class, transactionId, branchType.name(), status.name());
        return count != null && count == 1;
    }
    @Override public boolean hasLedgerBranch(String transactionId, TccBranchType branchType) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_db.tcc_branch WHERE transaction_id=? AND branch_type=?",
                Integer.class, transactionId, branchType.name());
        return count != null && count > 0;
    }

    private void insert(String table, TccBranch branch) {
        jdbcTemplate.update("INSERT INTO " + table + " (branch_id,xid,branch_type,resource_id,transaction_id,amount_fen,status,rollback_type,barrier_version,created_at,updated_at) "
                        + "VALUES (SUBSTRING(SHA2(CONCAT(?,':',?,':',?),256),1,26),?,?,?,?,?,?,?,?,?,?)",
                branch.getXid(), branch.getBranchType().name(), branch.getResourceId(),
                branch.getXid(), branch.getBranchType().name(), branch.getResourceId(), branch.getTransactionId(), branch.getAmountFen(),
                branch.getStatus().name(), name(branch.getRollbackType()), branch.getBarrierVersion(), branch.getCreatedAt(), branch.getUpdatedAt());
    }
    private boolean update(String table, TccBranch branch, long expectedVersion) {
        return jdbcTemplate.update("UPDATE " + table + " SET status=?,rollback_type=?,barrier_version=?,updated_at=? WHERE xid=? AND branch_type=? AND resource_id=? AND barrier_version=?",
                branch.getStatus().name(), name(branch.getRollbackType()), branch.getBarrierVersion(), branch.getUpdatedAt(),
                branch.getXid(), branch.getBranchType().name(), branch.getResourceId(), expectedVersion) == 1;
    }

    private Optional<TccBranch> first(ResultSet rs) throws SQLException {
        if (!rs.next()) return Optional.empty();
        String rollback = rs.getString("rollback_type");
        return Optional.of(new TccBranch(rs.getString("xid"), TccBranchType.valueOf(rs.getString("branch_type")),
                rs.getString("resource_id"), rs.getString("transaction_id"), rs.getLong("amount_fen"),
                TccBranchStatus.valueOf(rs.getString("status")), rollback == null ? null : RollbackType.valueOf(rollback),
                rs.getLong("barrier_version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()));
    }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
}
