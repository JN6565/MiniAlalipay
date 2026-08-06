package com.minialalipay.account.infrastructure.ledger;

import com.minialalipay.account.domain.ledger.LedgerAccount;
import com.minialalipay.account.domain.ledger.LedgerAccountClass;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import com.minialalipay.account.domain.ledger.LedgerAccountStatus;
import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerOwnerType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 使用 ledger_db.ledger_account 实现不可变科目身份仓储。 */
@Repository
public class LedgerAccountRepositoryImpl implements LedgerAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public LedgerAccountRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<LedgerAccount> findUserBalanceByUserId(String userId) {
        return jdbcTemplate.query("SELECT * FROM ledger_db.ledger_account WHERE owner_type='USER' "
                        + "AND owner_id=? AND account_type='USER_BALANCE_LIABILITY' AND currency='CNY'",
                this::map,
                userId).stream().findFirst();
    }

    @Override
    public Optional<LedgerAccount> findCreditReceivableByCreditAccountId(String creditAccountId) {
        return jdbcTemplate.query("SELECT * FROM ledger_db.ledger_account WHERE owner_type='CREDIT_ACCOUNT' "
                        + "AND owner_id=? AND account_type='CREDIT_RECEIVABLE_ASSET' AND currency='CNY'",
                this::map, creditAccountId).stream().findFirst();
    }

    @Override
    public void create(LedgerAccount account) {
        jdbcTemplate.update("INSERT INTO ledger_db.ledger_account "
                        + "(ledger_account_id,owner_type,owner_id,account_code,account_type,account_class,"
                        + "normal_direction,currency,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                account.getLedgerAccountId(), account.getOwnerType().name(), account.getOwnerId(),
                account.getAccountCode(), account.getAccountType(), account.getAccountClass().name(),
                account.getNormalDirection().name(), account.getCurrency(), account.getStatus().name(),
                account.getCreatedAt(), account.getUpdatedAt());
    }

    private LedgerAccount map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new LedgerAccount(rs.getString("ledger_account_id"),
                LedgerOwnerType.valueOf(rs.getString("owner_type")), rs.getString("owner_id"),
                rs.getString("account_code"), rs.getString("account_type"),
                LedgerAccountClass.valueOf(rs.getString("account_class")),
                LedgerDirection.valueOf(rs.getString("normal_direction")), rs.getString("currency"),
                LedgerAccountStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}
