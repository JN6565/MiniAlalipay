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
        assertTrue(sql.contains(
                "unique key uk_fund_transaction_source (source_type,source_order_id)"),
                "资金交易来源唯一键必须同时覆盖 source_type 和 source_order_id");
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

    @Test
    void 阶段五迁移包含非资金场景与充值额度约束() throws Exception {
        String scenarioSql = readMigration("/db/migration/V202608051210__create_non_fund_scenario_tables.sql");
        String rechargeSql = readMigration("/db/migration/V202608051212__create_recharge_policy_and_order_tables.sql");
        String manualCaseIdempotencySql = readMigration("/db/migration/V202608051213__create_manual_case_decision_idempotency.sql");
        String collectionSseSql = readMigration("/db/migration/V202608051215__create_collection_order_event.sql");
        String qrPaySseSql = readMigration("/db/migration/V202608052130__create_qr_pay_order_event.sql");

        assertTrue(scenarioSql.contains("create table if not exists business_db.qr_pay_token"));
        assertTrue(scenarioSql.contains("token_digest binary(32)"));
        assertTrue(!scenarioSql.contains("raw_token"));
        assertTrue(scenarioSql.contains("unique key uk_personal_collection_code_active_owner"));
        assertTrue(scenarioSql.contains("create table if not exists business_db.collection_request"));
        assertTrue(scenarioSql.contains("create table if not exists business_db.risk_decision"));
        assertTrue(rechargeSql.contains("create table if not exists business_db.recharge_policy"));
        assertTrue(rechargeSql.contains("25000000"));
        assertTrue(rechargeSql.contains("daily_count_limit"));
        assertTrue(rechargeSql.contains("'01k22rechargepolicy0000001'"));
        assertTrue(manualCaseIdempotencySql.contains("uk_manual_case_decision_idempotency"));
        String collectionPaymentSql = readMigration("/db/migration/V202608051214__align_collection_order_funding_and_terminal_states.sql");
        assertTrue(collectionPaymentSql.contains("add column funding_source"));
        assertTrue(collectionPaymentSql.contains("funding_source = 'balance'"));
        assertTrue(collectionPaymentSql.contains("'success'"));
        assertTrue(collectionPaymentSql.contains("'manual_review'"));
        assertTrue(collectionSseSql.contains("create table if not exists business_db.collection_order_event"));
        assertTrue(collectionSseSql.contains("idx_collection_order_event_request_replay"));
        assertTrue(collectionSseSql.contains("retention_until"));
        assertTrue(!collectionSseSql.contains("payer_account_id"));
        assertTrue(!collectionSseSql.contains("payee_account_id"));
        assertTrue(!collectionSseSql.contains("confirmation_token"));
        assertTrue(qrPaySseSql.contains("create table if not exists business_db.qr_pay_order_event"));
        assertTrue(qrPaySseSql.contains("idx_qr_pay_order_event_replay"));
        assertTrue(qrPaySseSql.contains("retention_until"));
        assertTrue(!qrPaySseSql.contains("payer_account_id"));
        assertTrue(!qrPaySseSql.contains("payee_account_id"));
        assertTrue(!qrPaySseSql.contains("confirmation_token"));
        assertTrue(!scenarioSql.contains("account_db."));
        assertTrue(!rechargeSql.contains("account_db."));
        assertTrue(!rechargeSql.contains("ledger_db."));
    }

    @Test
    void 状态约束修正迁移保留风控与资金终态() throws Exception {
        String restoreSql = readMigration("/db/migration/V202608061000__restore_collection_order_terminal_states.sql");
        assertTrue(restoreSql.contains("drop check ck_collection_order_status"));
        assertTrue(restoreSql.contains("'risk_review'"),
                "C2C 订单状态约束必须保留受理前风控复核 RISK_REVIEW");
        assertTrue(restoreSql.contains("'success'") && restoreSql.contains("'manual_review'"),
                "统一交易终态发布器回填的 SUCCESS/MANUAL_REVIEW 必须保留，否则成功支付会被 CHECK 约束拒绝回滚");
        assertTrue(!restoreSql.contains("account_db."), "本迁移只能修改 business_db");
        assertTrue(!restoreSql.contains("ledger_db."), "本迁移只能修改 business_db");
    }

    @Test
    void 告警规则迁移创建独立运营投影表且种子规则满足P0() throws Exception {
        String alertRuleSql = readMigration("/db/migration/V202608061100__create_monitor_alert_rule.sql");
        assertTrue(alertRuleSql.contains("create table if not exists metrics_db.monitor_alert_rule"));
        assertTrue(alertRuleSql.contains("threshold_value bigint unsigned not null"));
        assertTrue(alertRuleSql.contains("version bigint unsigned not null default 0"));
        assertTrue(alertRuleSql.contains("'duplicate_charge'") && alertRuleSql.contains("'ledger_imbalance'"),
                "P0 告警种子规则必须存在");
        assertTrue(!alertRuleSql.contains("account_db.") && !alertRuleSql.contains("business_db."),
                "告警规则表属于 metrics_db 运营投影，不允许访问资金 Schema");
    }

    private String readMigration(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertTrue(input != null, "迁移文件必须存在: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
    }
}
