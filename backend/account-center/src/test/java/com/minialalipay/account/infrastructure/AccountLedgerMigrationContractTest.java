package com.minialalipay.account.infrastructure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AccountLedgerMigrationContractTest {

    @Test
    void migrationsKeepSchemaOwnershipAndDefineCriticalConstraints() throws IOException {
        Path migrationDirectory = Path.of("src", "main", "resources", "db", "migration");
        String accountSql = Files.readString(migrationDirectory.resolve(
                "V202608051000__create_account_core_tables.sql"));
        String ledgerSql = Files.readString(migrationDirectory.resolve(
                "V202608051010__create_ledger_core_tables.sql"));

        assertThat(accountSql).contains("CREATE TABLE IF NOT EXISTS account_db.account (")
                .contains("CREATE TABLE IF NOT EXISTS account_db.account_balance (")
                .contains("CREATE TABLE IF NOT EXISTS account_db.freeze_record (")
                .contains("registration_id CHAR(26) NOT NULL")
                .contains("ck_freeze_purpose")
                .contains("CHECK (available_fen >= 0 AND frozen_fen >= 0)")
                .doesNotContain("ledger_db.");
        assertThat(ledgerSql).contains("CREATE TABLE IF NOT EXISTS ledger_db.ledger_account (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.ledger_voucher (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.ledger_entry (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.outbox_event (")
                .contains("reversal_reason VARCHAR(32) NULL")
                .contains("CHECK (total_debit_fen = total_credit_fen AND total_debit_fen > 0)")
                .contains("CHECK (amount_fen > 0)")
                .doesNotContain("account_db.");
    }
}
