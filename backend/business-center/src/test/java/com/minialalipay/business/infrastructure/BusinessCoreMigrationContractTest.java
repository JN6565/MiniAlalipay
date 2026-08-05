package com.minialalipay.business.infrastructure;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessCoreMigrationContractTest {

    @Test
    void 迁移包含来源唯一幂等令牌与恢复索引() throws Exception {
        String path = "/db/migration/V202608041600__create_transfer_transaction_core.sql";
        String sql;
        try (var input = getClass().getResourceAsStream(path)) {
            assertTrue(input != null, "阶段四业务库迁移必须存在");
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertTrue(sql.contains("create table if not exists business_db.transfer_draft"));
        assertTrue(sql.contains("unique key uk_fund_transaction_source"));
        assertTrue(sql.contains("unique key uk_fund_transaction_idempotency"));
        assertTrue(sql.contains("unique key uk_confirmation_token"));
        assertTrue(sql.contains("key idx_fund_transaction_recovery"));
        assertTrue(sql.contains("create table if not exists business_db.tcc_global"));
        assertTrue(sql.contains("create table if not exists business_db.manual_case"));
        assertTrue(sql.contains("uk_manual_case_active_subject"));
        assertTrue(sql.contains("create table if not exists business_db.outbox_event"));
        assertTrue(!sql.contains("account_db."));
        assertTrue(!sql.contains("ledger_db."));
    }
}
