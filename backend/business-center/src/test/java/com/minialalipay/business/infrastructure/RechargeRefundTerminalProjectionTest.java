package com.minialalipay.business.infrastructure;

import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.business.infrastructure.persistence.JdbcBusinessStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 充值、退款终态必须在统一资金交易终态发布事务内投影到来源订单并收敛当日额度。 */
class RechargeRefundTerminalProjectionTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void 充值成功终态投影订单并结算当日额度为成功() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('recharge-tx','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-r','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.recharge_order VALUES ('recharge-order-1','recharge-tx','user-1','account-1',100,DATE '2026-08-05','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.recharge_daily_usage VALUES ('user-1',DATE '2026-08-05',100,0,1,0,1,?)", NOW);
        FundTransaction transaction = FundTransaction.accept("recharge-tx", TransactionType.RECHARGE,
                SourceType.RECHARGE_ORDER, "recharge-order-1", "user-1", null, "account-1",
                FundingSource.SYSTEM_ISSUANCE, 100L, "123e4567-e89b-12d3-a456-426614174000", "LOW",
                "0".repeat(32), NOW);
        transaction.publishSuccess(true, NOW.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-r", "SUCCESS", "event-r", NOW.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.recharge_order WHERE recharge_order_id='recharge-order-1'", String.class))
                .isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT processing_fen FROM business_db.recharge_daily_usage WHERE user_id='user-1' AND business_date=DATE '2026-08-05'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT success_fen FROM business_db.recharge_daily_usage WHERE user_id='user-1' AND business_date=DATE '2026-08-05'", Long.class))
                .isEqualTo(100L);
        assertThat(jdbc.queryForObject("SELECT success_count FROM business_db.recharge_daily_usage WHERE user_id='user-1' AND business_date=DATE '2026-08-05'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 充值取消终态投影订单并释放此前预占额度() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('recharge-cancel-tx','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-rc','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.recharge_order VALUES ('recharge-order-2','recharge-cancel-tx','user-1','account-1',100,DATE '2026-08-05','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.recharge_daily_usage VALUES ('user-1',DATE '2026-08-05',100,0,1,0,1,?)", NOW);
        FundTransaction transaction = FundTransaction.accept("recharge-cancel-tx", TransactionType.RECHARGE,
                SourceType.RECHARGE_ORDER, "recharge-order-2", "user-1", null, "account-1",
                FundingSource.SYSTEM_ISSUANCE, 100L, "123e4567-e89b-12d3-a456-426614174001", "LOW",
                "0".repeat(32), NOW);
        transaction.startCompensating(NOW.plusSeconds(1));
        transaction.publishCancelled(true, NOW.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-rc", "CANCELLED", "event-rc", NOW.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.recharge_order WHERE recharge_order_id='recharge-order-2'", String.class))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT processing_fen FROM business_db.recharge_daily_usage WHERE user_id='user-1' AND business_date=DATE '2026-08-05'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT processing_count FROM business_db.recharge_daily_usage WHERE user_id='user-1' AND business_date=DATE '2026-08-05'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT success_fen FROM business_db.recharge_daily_usage WHERE user_id='user-1' AND business_date=DATE '2026-08-05'", Long.class))
                .isZero();
    }

    @Test
    void 退款成功终态投影订单并回填完成时间() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('refund-tx','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-rf','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.refund_order VALUES ('refund-order-1','refund-tx','PROCESSING',0,NULL,?)", NOW);
        FundTransaction transaction = FundTransaction.acceptRefund("refund-tx", SourceType.REFUND_ORDER,
                "refund-order-1", "merchant-user-1", "merchant-account-1", "payer-account-1",
                FundingSource.BALANCE, 100L, "123e4567-e89b-12d3-a456-426614174002", "LOW",
                "0".repeat(32), null, NOW);
        transaction.publishSuccess(true, NOW.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-rf", "SUCCESS", "event-rf", NOW.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.refund_order WHERE refund_order_id='refund-order-1'", String.class))
                .isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT completed_at FROM business_db.refund_order WHERE refund_order_id='refund-order-1'", Date.class))
                .isNotNull();
    }

    @Test
    void 退款取消终态投影订单并回填完成时间() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('refund-cancel-tx','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-rfc','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.refund_order VALUES ('refund-order-2','refund-cancel-tx','PROCESSING',0,NULL,?)", NOW);
        FundTransaction transaction = FundTransaction.acceptRefund("refund-cancel-tx", SourceType.REFUND_ORDER,
                "refund-order-2", "merchant-user-1", "merchant-account-1", "payer-account-1",
                FundingSource.BALANCE, 100L, "123e4567-e89b-12d3-a456-426614174003", "LOW",
                "0".repeat(32), null, NOW);
        transaction.startCompensating(NOW.plusSeconds(1));
        transaction.publishCancelled(true, NOW.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-rfc", "CANCELLED", "event-rfc", NOW.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.refund_order WHERE refund_order_id='refund-order-2'", String.class))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT completed_at FROM business_db.refund_order WHERE refund_order_id='refund-order-2'", Date.class))
                .isNotNull();
    }

    @Test
    void 退款人工复核终态保留订单等待处置且不回填完成时间() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('refund-manual-tx','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-rfm','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.refund_order VALUES ('refund-order-3','refund-manual-tx','PROCESSING',0,NULL,?)", NOW);
        FundTransaction transaction = FundTransaction.acceptRefund("refund-manual-tx", SourceType.REFUND_ORDER,
                "refund-order-3", "merchant-user-1", "merchant-account-1", "payer-account-1",
                FundingSource.BALANCE, 100L, "123e4567-e89b-12d3-a456-426614174004", "LOW",
                "0".repeat(32), null, NOW);
        transaction.requireManualReview(NOW.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-rfm", "MANUAL_REVIEW", "event-rfm", NOW.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.refund_order WHERE refund_order_id='refund-order-3'", String.class))
                .isEqualTo("MANUAL_REVIEW");
        assertThat(jdbc.queryForObject("SELECT completed_at FROM business_db.refund_order WHERE refund_order_id='refund-order-3'", Date.class))
                .isNull();
    }

    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:recharge_refund_projection_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static void createTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS business_db");
        jdbc.execute("CREATE TABLE business_db.fund_transaction (transaction_id VARCHAR(26) PRIMARY KEY,status VARCHAR(32),version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.tcc_global (xid VARCHAR(64) PRIMARY KEY,status VARCHAR(32),updated_at TIMESTAMP,next_retry_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.recharge_order (recharge_order_id VARCHAR(26) PRIMARY KEY,transaction_id VARCHAR(26),user_id VARCHAR(26),target_account_id VARCHAR(26),amount_fen BIGINT,business_date DATE,status VARCHAR(32),version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.recharge_daily_usage (user_id VARCHAR(26),business_date DATE,processing_fen BIGINT,success_fen BIGINT,processing_count INT,success_count INT,version BIGINT,updated_at TIMESTAMP,PRIMARY KEY (user_id,business_date))");
        jdbc.execute("CREATE TABLE business_db.refund_order (refund_order_id VARCHAR(26) PRIMARY KEY,transaction_id VARCHAR(26),status VARCHAR(32),version BIGINT,completed_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.outbox_event (event_id VARCHAR(64) PRIMARY KEY,aggregate_type VARCHAR(32),aggregate_id VARCHAR(26),aggregate_version BIGINT,event_type VARCHAR(64),event_version INT,transaction_id VARCHAR(26),producer VARCHAR(32),trace_id VARCHAR(32),occurred_at TIMESTAMP,payload VARCHAR(512),created_at TIMESTAMP)");
    }
}
