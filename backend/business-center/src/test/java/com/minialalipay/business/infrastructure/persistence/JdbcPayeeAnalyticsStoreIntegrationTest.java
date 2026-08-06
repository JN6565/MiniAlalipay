package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.PayeeAnalyticsStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 收款分析投影查询的 H2 集成测试：终态过滤、订单去重与退款冲减口径。 */
class JdbcPayeeAnalyticsStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    private JdbcTemplate jdbc;
    private JdbcPayeeAnalyticsStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:analytics_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS business_db");
        jdbc.execute("CREATE TABLE business_db.fund_transaction (transaction_id VARCHAR(26) PRIMARY KEY,"
                + "business_type VARCHAR(16),source_type VARCHAR(32),source_order_id VARCHAR(26),"
                + "initiator_user_id VARCHAR(26),payer_account_id VARCHAR(26),payee_account_id VARCHAR(26),"
                + "funding_source VARCHAR(16),amount_fen BIGINT,idempotency_key VARCHAR(64),"
                + "status VARCHAR(32),risk_level VARCHAR(16),trace_id VARCHAR(32),version BIGINT,"
                + "created_at TIMESTAMP,updated_at TIMESTAMP)");
        store = new JdbcPayeeAnalyticsStore(jdbc);
    }

    private void insert(String txId, String type, String orderId, String payer, String payee,
                        long amount, String status, Instant at) {
        jdbc.update("INSERT INTO business_db.fund_transaction (transaction_id,business_type,source_type,source_order_id,"
                        + "initiator_user_id,payer_account_id,payee_account_id,funding_source,amount_fen,"
                        + "idempotency_key,status,risk_level,trace_id,version,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                txId, type, "QR_PAY_ORDER", orderId, payer, payer, payee, "BALANCE", amount,
                "key-" + txId, status, "LOW", "0".repeat(32), 0, Timestamp.from(at), Timestamp.from(at));
    }

    @Test
    void 只统计本人成功收款并按订单去重且退款冲减净收款() {
        // 本人收款：两笔 SUCCESS QR_PAY（其中 source_order 相同验证去重），一笔 SUCCESS CREDIT_PAY。
        insert("t-1", "QR_PAY", "order-1", "payer-1", "acc-me", 100, "SUCCESS", NOW);
        insert("t-2", "QR_PAY", "order-1", "payer-1", "acc-me", 100, "SUCCESS", NOW);
        insert("t-3", "QR_PAY", "order-2", "payer-2", "acc-me", 200, "SUCCESS", NOW);
        insert("t-4", "CREDIT_PAY", "order-3", "payer-3", "acc-me", 500, "SUCCESS", NOW);
        // 本人退款发出：REFUND 冲减净收款。
        insert("t-5", "REFUND", "refund-1", "acc-me", "payer-1", 50, "SUCCESS", NOW);
        // 不计入：处理中交易、其他收款人交易、统计区间之前的交易。
        insert("t-6", "QR_PAY", "order-4", "payer-4", "acc-me", 300, "PROCESSING", NOW);
        insert("t-7", "QR_PAY", "order-5", "payer-5", "acc-other", 900, "SUCCESS", NOW);
        insert("t-8", "QR_PAY", "order-6", "payer-6", "acc-me", 700, "SUCCESS", NOW.minusSeconds(400));

        PayeeAnalyticsStore.PayeeAnalytics result = store.analytics("acc-me", NOW.minusSeconds(300), NOW);

        // 去重后订单数 = order-1, order-2, order-3 = 3；交易数 = 4（t-1,t-2,t-3,t-4）。
        assertThat(result.orderCount()).isEqualTo(3);
        assertThat(result.transactionCount()).isEqualTo(4);
        // 收款总额 = 100+100+200+500 = 900；退款 = 50；净收款 = 850。
        assertThat(result.grossAmountFen()).isEqualTo(900);
        assertThat(result.refundAmountFen()).isEqualTo(50);
        assertThat(result.netAmountFen()).isEqualTo(850);
        assertThat(result.byPaymentMethod()).hasSize(2);
        assertThat(result.byPaymentMethod().get(0).businessType()).isEqualTo("CREDIT_PAY");
        assertThat(result.byPaymentMethod().get(0).amountFen()).isEqualTo(500);
        assertThat(result.byPaymentMethod().get(1).businessType()).isEqualTo("QR_PAY");
        assertThat(result.byPaymentMethod().get(1).orderCount()).isEqualTo(2);
    }

    @Test
    void 无收款记录时全部统计归零() {
        PayeeAnalyticsStore.PayeeAnalytics result = store.analytics("acc-empty", NOW.minusSeconds(300), NOW);

        assertThat(result.orderCount()).isZero();
        assertThat(result.transactionCount()).isZero();
        assertThat(result.grossAmountFen()).isZero();
        assertThat(result.refundAmountFen()).isZero();
        assertThat(result.netAmountFen()).isZero();
        assertThat(result.byPaymentMethod()).isEmpty();
    }
}
