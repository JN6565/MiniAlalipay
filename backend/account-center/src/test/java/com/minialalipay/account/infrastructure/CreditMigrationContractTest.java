package com.minialalipay.account.infrastructure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验已执行信用迁移仍与数据库设计中的表归属、金额约束和幂等唯一键一致。
 */
class CreditMigrationContractTest {

    @Test
    void 信用迁移包含全部设计表和关键金融约束() throws IOException {
        Path migration = Path.of(
                "src", "main", "resources", "db", "migration",
                "V202608050900__create_credit_tables.sql");
        String sql = Files.readString(migration);

        assertThat(sql)
                .contains("CREATE TABLE IF NOT EXISTS account_db.credit_account (")
                .contains("CREATE TABLE IF NOT EXISTS account_db.credit_freeze (")
                .contains("CREATE TABLE IF NOT EXISTS account_db.credit_repayment_draft (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.credit_receivable (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.credit_purchase (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.credit_bill (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.credit_bill_item (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.credit_repayment (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.credit_repayment_allocation (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.credit_repayment_allocation_detail (")
                .contains("CREATE TABLE IF NOT EXISTS ledger_db.credit_job_run (")
                .contains("CONSTRAINT ck_credit_account_limit CHECK (total_limit_fen = 500000)")
                .contains("CONSTRAINT ck_credit_account_usage CHECK (used_fen + frozen_fen <= total_limit_fen)")
                .contains("UNIQUE KEY uk_credit_freeze_transaction_account (transaction_id, credit_account_id)")
                .contains("CONSTRAINT ck_credit_receivable_overdue CHECK (overdue_fen <= billed_fen)")
                .contains("UNIQUE KEY uk_credit_purchase_transaction (credit_transaction_id)")
                .contains("UNIQUE KEY uk_credit_bill_item_purchase (purchase_id)")
                .contains("UNIQUE KEY uk_credit_repayment_transaction (transaction_id)")
                .contains("UNIQUE KEY uk_credit_job_type_date (job_type, business_date)");
    }
}
