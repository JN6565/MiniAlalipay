package com.minialalipay.account.infrastructure;

import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.account.FreezePurpose;
import com.minialalipay.account.domain.account.FreezeRecord;
import com.minialalipay.account.domain.account.FreezeRecordRepository;
import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.ledger.LedgerVoucher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false"})
@Transactional
class AccountLedgerRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-04T08:00:00Z");

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AccountRepository accountRepository;
    @Autowired private FreezeRecordRepository freezeRecordRepository;
    @Autowired private LedgerRepository ledgerRepository;

    @BeforeEach
    void createTables() {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS account_db");
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS ledger_db");
        jdbcTemplate.execute("DROP TABLE IF EXISTS ledger_db.ledger_entry");
        jdbcTemplate.execute("DROP TABLE IF EXISTS ledger_db.ledger_voucher");
        jdbcTemplate.execute("DROP TABLE IF EXISTS ledger_db.ledger_account");
        jdbcTemplate.execute("DROP TABLE IF EXISTS ledger_db.outbox_event");
        jdbcTemplate.execute("DROP TABLE IF EXISTS account_db.freeze_record");
        jdbcTemplate.execute("DROP TABLE IF EXISTS account_db.account_balance");
        jdbcTemplate.execute("DROP TABLE IF EXISTS account_db.account");
        jdbcTemplate.execute("CREATE TABLE account_db.account (account_id VARCHAR(26) PRIMARY KEY, "
                + "user_id VARCHAR(26), registration_id VARCHAR(26) UNIQUE, account_type VARCHAR(16), "
                + "currency VARCHAR(3), status VARCHAR(16), version BIGINT, created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE account_db.account_balance (account_id VARCHAR(26) PRIMARY KEY, "
                + "available_fen BIGINT, frozen_fen BIGINT, version BIGINT, updated_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE account_db.freeze_record (freeze_id VARCHAR(26) PRIMARY KEY, "
                + "transaction_id VARCHAR(26), account_id VARCHAR(26), purpose VARCHAR(24), amount_fen BIGINT, "
                + "status VARCHAR(16), branch_xid VARCHAR(128), version BIGINT, created_at TIMESTAMP, updated_at TIMESTAMP, "
                + "UNIQUE(transaction_id, account_id, purpose))");
        jdbcTemplate.execute("CREATE TABLE ledger_db.ledger_account (ledger_account_id VARCHAR(26) PRIMARY KEY, "
                + "owner_type VARCHAR(24), owner_id VARCHAR(64), account_code VARCHAR(64), account_type VARCHAR(32), "
                + "account_class VARCHAR(16), normal_direction VARCHAR(8), currency VARCHAR(3), status VARCHAR(16), "
                + "created_at TIMESTAMP, updated_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE ledger_db.ledger_voucher (voucher_id VARCHAR(26) PRIMARY KEY, "
                + "transaction_id VARCHAR(26), voucher_type VARCHAR(24), reversal_no SMALLINT, original_voucher_id VARCHAR(26), "
                + "reversal_reason VARCHAR(32), status VARCHAR(16), total_debit_fen BIGINT, total_credit_fen BIGINT, "
                + "posted_at TIMESTAMP, created_at TIMESTAMP, "
                + "UNIQUE(transaction_id, voucher_type, reversal_no))");
        jdbcTemplate.execute("CREATE TABLE ledger_db.ledger_entry (entry_id BIGINT PRIMARY KEY, voucher_id VARCHAR(26), "
                + "transaction_id VARCHAR(26), ledger_account_id VARCHAR(26), direction VARCHAR(8), amount_fen BIGINT, "
                + "sequence_no SMALLINT, memo VARCHAR(255), created_at TIMESTAMP, UNIQUE(voucher_id, sequence_no))");
        jdbcTemplate.execute("CREATE TABLE ledger_db.outbox_event (event_id VARCHAR(26) PRIMARY KEY, "
                + "aggregate_type VARCHAR(32), aggregate_id VARCHAR(26), aggregate_version BIGINT, "
                + "event_type VARCHAR(64), event_version SMALLINT, business_type VARCHAR(16), "
                + "transaction_id VARCHAR(26), producer VARCHAR(32), trace_id VARCHAR(32), occurred_at TIMESTAMP, "
                + "payload VARCHAR(1000), status VARCHAR(16), retry_count INT, created_at TIMESTAMP)");
    }

    @Test
    void persistsAccountBalanceAndFreezeWithCas() {
        Account account = Account.open("account", "user", "registration", NOW);
        AccountBalance balance = new AccountBalance("account", 1_000L, 0L, 0L, NOW);
        accountRepository.create(account, balance);

        AccountBalance loaded = accountRepository.findBalance("account").orElseThrow();
        loaded.freeze(300L, NOW.plusSeconds(1));
        assertThat(accountRepository.updateBalance(loaded, 0L)).isTrue();
        assertThat(accountRepository.updateBalance(loaded, 0L)).isFalse();
        jdbcTemplate.update("UPDATE account_db.account SET status='FROZEN' WHERE account_id='account'");
        AccountBalance next = accountRepository.findBalance("account").orElseThrow();
        next.cancel(300L, NOW.plusSeconds(2));
        assertThat(accountRepository.updateBalanceForActiveAccount(next, 1L)).isFalse();

        FreezeRecord record = FreezeRecord.create("freeze", "transaction", "account",
                FreezePurpose.TRANSFER_OUT, 300L, "xid", NOW);
        freezeRecordRepository.create(record);
        record.confirm(NOW.plusSeconds(2));
        assertThat(freezeRecordRepository.update(record, 0L)).isTrue();
        assertThat(freezeRecordRepository.find("transaction", "account", FreezePurpose.TRANSFER_OUT))
                .get().extracting(FreezeRecord::getStatus).hasToString("CONFIRMED");
    }

    @Test
    void persistsBalancedVoucherAndListsEntriesByOwner() {
        jdbcTemplate.update("INSERT INTO ledger_db.ledger_account VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                "payer-ledger", "USER", "user", "USER_BALANCE_user", "USER_BALANCE_LIABILITY",
                "LIABILITY", "CREDIT", "CNY", "ACTIVE", NOW, NOW);
        LedgerVoucher voucher = LedgerVoucher.prepare("voucher", "transaction", "TRANSFER", 0, null,
                500L, 500L, List.of(
                        new LedgerEntry(1L, "voucher", "transaction", "payer-ledger", LedgerDirection.DEBIT,
                                500L, 1, "付款", NOW),
                        new LedgerEntry(2L, "voucher", "transaction", "payee-ledger", LedgerDirection.CREDIT,
                                500L, 2, "收款", NOW)
                ), NOW);
        ledgerRepository.savePrepared(voucher);

        assertThat(ledgerRepository.find("transaction", "TRANSFER", 0)).isPresent();
        assertThat(ledgerRepository.find("transaction", "TRANSFER", 0).orElseThrow().getStatus().name())
                .isEqualTo("PREPARED");
        assertThat(ledgerRepository.summarizeEntries("voucher"))
                .extracting(LedgerRepository.LedgerTotals::debitFen,
                        LedgerRepository.LedgerTotals::creditFen)
                .containsExactly(500L, 500L);
        voucher.post(NOW.plusSeconds(1));
        assertThat(ledgerRepository.postAndAppendOutbox(voucher, "event-00000000000000000001",
                "trace-00000000000000000000000000", NOW.plusSeconds(1))).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_db.outbox_event", Integer.class))
                .isEqualTo(1);
        assertThat(ledgerRepository.findEntriesByUserId("user", null, 0L, 100))
                .extracting(LedgerEntry::entryId).containsExactly(1L);
    }
}
