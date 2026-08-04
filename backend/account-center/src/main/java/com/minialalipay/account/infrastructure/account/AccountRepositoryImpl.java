package com.minialalipay.account.infrastructure.account;

import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.account.AccountStatus;
import com.minialalipay.account.domain.account.AccountType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 使用 account_db 实现账户与余额仓储端口。 */
@Repository
public class AccountRepositoryImpl implements AccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Account> findByRegistrationId(String registrationId) {
        return first(jdbcTemplate.query("SELECT * FROM account_db.account WHERE registration_id = ?",
                this::mapAccount, registrationId));
    }

    @Override
    public Optional<Account> findByUserId(String userId) {
        return first(jdbcTemplate.query("SELECT * FROM account_db.account "
                        + "WHERE user_id = ? AND account_type = 'PERSONAL' AND currency = 'CNY'",
                this::mapAccount, userId));
    }

    @Override
    public Optional<Account> findById(String accountId) {
        return first(jdbcTemplate.query("SELECT * FROM account_db.account WHERE account_id = ?",
                this::mapAccount, accountId));
    }

    @Override
    public Optional<AccountBalance> findBalance(String accountId) {
        return first(jdbcTemplate.query("SELECT * FROM account_db.account_balance WHERE account_id = ?",
                this::mapBalance, accountId));
    }

    @Override
    public void create(Account account, AccountBalance balance) {
        jdbcTemplate.update("INSERT INTO account_db.account "
                        + "(account_id,user_id,registration_id,account_type,currency,status,version,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                account.getAccountId(), account.getUserId(), account.getRegistrationId(),
                account.getAccountType().name(), account.getCurrency(), account.getStatus().name(),
                account.getVersion(), account.getCreatedAt(), account.getUpdatedAt());
        jdbcTemplate.update("INSERT INTO account_db.account_balance "
                        + "(account_id,available_fen,frozen_fen,version,updated_at) VALUES (?,?,?,?,?)",
                balance.getAccountId(), balance.getAvailableFen(), balance.getFrozenFen(),
                balance.getVersion(), balance.getUpdatedAt());
    }

    @Override
    public boolean updateBalance(AccountBalance balance, long expectedVersion) {
        int updated = jdbcTemplate.update("UPDATE account_db.account_balance SET available_fen=?, frozen_fen=?, "
                        + "version=version+1, updated_at=? WHERE account_id=? AND version=?",
                balance.getAvailableFen(), balance.getFrozenFen(), balance.getUpdatedAt(),
                balance.getAccountId(), expectedVersion);
        if (updated == 1) balance.updateVersion(expectedVersion + 1);
        return updated == 1;
    }

    @Override
    public boolean updateBalanceForActiveAccount(AccountBalance balance, long expectedVersion) {
        int updated = jdbcTemplate.update("UPDATE account_db.account_balance SET available_fen=?, frozen_fen=?, "
                        + "version=version+1, updated_at=? WHERE account_id=? AND version=? "
                        + "AND EXISTS (SELECT 1 FROM account_db.account a "
                        + "WHERE a.account_id=account_db.account_balance.account_id AND a.status='ACTIVE')",
                balance.getAvailableFen(), balance.getFrozenFen(), balance.getUpdatedAt(),
                balance.getAccountId(), expectedVersion);
        if (updated == 1) balance.updateVersion(expectedVersion + 1);
        return updated == 1;
    }

    private Account mapAccount(ResultSet rs, int rowNum) throws SQLException {
        return new Account(rs.getString("account_id"), rs.getString("user_id"),
                rs.getString("registration_id"), AccountType.valueOf(rs.getString("account_type")),
                rs.getString("currency"), AccountStatus.valueOf(rs.getString("status")),
                rs.getLong("version"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private AccountBalance mapBalance(ResultSet rs, int rowNum) throws SQLException {
        return new AccountBalance(rs.getString("account_id"), rs.getLong("available_fen"),
                rs.getLong("frozen_fen"), rs.getLong("version"), instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static <T> Optional<T> first(List<T> values) {
        return values.stream().findFirst();
    }
}
