package com.minialalipay.business.infrastructure;

import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.business.infrastructure.persistence.JdbcBusinessStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 统一交易终态必须在本地事务内投影到固定请求、来源订单和 SSE 事件。 */
class CollectionTerminalProjectionTest {
    @Test
    void 终态发布成功时原子回填固定请求订单和可重放事件() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-1','PROCESSING',0,?)", now);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-1','PROCESSING',?,NULL)", now);
        jdbc.update("INSERT INTO business_db.collection_order VALUES ('order-1','request-1','transaction-1','PROCESSING',0,?)", now);
        jdbc.update("INSERT INTO business_db.collection_request VALUES ('request-1','order-1','transaction-1','PROCESSING',0,?)", now);
        FundTransaction transaction = FundTransaction.accept("transaction-1", TransactionType.TRANSFER,
                SourceType.COLLECTION_REQUEST_ORDER, "order-1", "payer-1", "account-payer-1", "account-payee-1",
                FundingSource.BALANCE, 5200L, "123e4567-e89b-12d3-a456-426614174000", "LOW", "0".repeat(32), now);
        transaction.publishSuccess(true, now.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-1", "SUCCESS", "event-1", now.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_order WHERE order_id='order-1'", String.class)).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_request WHERE request_id='request-1'", String.class)).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_order_event WHERE event_id='event-1'", String.class)).isEqualTo("SUCCESS");
    }

    @Test
    void 二维码交易状态只能由统一交易主单投影() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        Instant now = Instant.parse("2026-08-05T12:00:00Z");
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-qr','PROCESSING',0,?)", now);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-qr','PROCESSING',?,NULL)", now);
        jdbc.update("INSERT INTO business_db.qr_pay_order VALUES ('qr-order-1','transaction-qr','PROCESSING',0,?)", now);
        FundTransaction transaction = FundTransaction.accept("transaction-qr", TransactionType.QR_PAY,
                SourceType.QR_PAY_ORDER, "qr-order-1", "payer-1", "account-payer-1", "account-payee-1",
                FundingSource.BALANCE, 5200L, "123e4567-e89b-12d3-a456-426614174001", "LOW", "0".repeat(32), now);
        transaction.publishSuccess(true, now.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-qr", "SUCCESS", "event-qr", now.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.qr_pay_order WHERE qr_order_id='qr-order-1'", String.class)).isEqualTo("SUCCESS");
    }

    private static JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:collection_projection_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private static void createTables(JdbcTemplate jdbc) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS business_db");
        jdbc.execute("CREATE TABLE business_db.fund_transaction (transaction_id VARCHAR(26) PRIMARY KEY,status VARCHAR(32),version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.tcc_global (xid VARCHAR(64) PRIMARY KEY,status VARCHAR(32),updated_at TIMESTAMP,next_retry_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.collection_order (order_id VARCHAR(26) PRIMARY KEY,request_id VARCHAR(26),transaction_id VARCHAR(26),status VARCHAR(32),version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.collection_request (request_id VARCHAR(26) PRIMARY KEY,active_order_id VARCHAR(26),transaction_id VARCHAR(26),status VARCHAR(32),version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.collection_order_event (event_id VARCHAR(64) PRIMARY KEY,order_id VARCHAR(26),request_id VARCHAR(26),transaction_id VARCHAR(26),status VARCHAR(32),occurred_at TIMESTAMP,retention_until TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.qr_pay_order (qr_order_id VARCHAR(26) PRIMARY KEY,transaction_id VARCHAR(26),status VARCHAR(32),version BIGINT,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.qr_pay_order_event (event_id VARCHAR(64) PRIMARY KEY,qr_order_id VARCHAR(26),transaction_id VARCHAR(26),status VARCHAR(32),occurred_at TIMESTAMP,retention_until TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.outbox_event (event_id VARCHAR(64) PRIMARY KEY,aggregate_type VARCHAR(32),aggregate_id VARCHAR(26),aggregate_version BIGINT,event_type VARCHAR(64),event_version INT,transaction_id VARCHAR(26),producer VARCHAR(32),trace_id VARCHAR(32),occurred_at TIMESTAMP,payload VARCHAR(512),created_at TIMESTAMP)");
    }
}
