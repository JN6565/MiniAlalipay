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
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 统一交易终态必须在本地事务内投影到固定请求、来源订单和 SSE 事件。
 *
 * <p>一码多收模型下 {@code collection_request.active_order_id} 已弃用恒为 NULL，
 * 请求终态必须等全部订单到达终态后收敛：存在成功订单则请求 SUCCESS，否则 CANCELLED。</p>
 */
class CollectionTerminalProjectionTest {

    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void 一码多收单笔订单成功时请求投影为成功并写入事件() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-1','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-1','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.collection_order VALUES ('order-1','request-1','transaction-1','PROCESSING',0,?)", NOW);
        // 一码多收真实形态：active_order_id 弃用恒为 NULL
        jdbc.update("INSERT INTO business_db.collection_request VALUES ('request-1',NULL,NULL,'PROCESSING',0,?)", NOW);
        FundTransaction transaction = acceptedCollectionTransaction("transaction-1", "order-1");
        transaction.publishSuccess(true, NOW.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-1", "SUCCESS", "event-1", NOW.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_order WHERE order_id='order-1'", String.class)).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_request WHERE request_id='request-1'", String.class)).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM business_db.collection_request WHERE request_id='request-1'", String.class)).isEqualTo("transaction-1");
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_order_event WHERE event_id='event-1'", String.class)).isEqualTo("SUCCESS");
    }

    @Test
    void 仍有在途订单时请求保持处理中且不抛异常() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-1','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-1','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.collection_order VALUES ('order-1','request-1','transaction-1','PROCESSING',0,?)", NOW);
        // 另一付款人的订单仍在受理中，请求不得提前进入终态
        jdbc.update("INSERT INTO business_db.collection_order VALUES ('order-2','request-1',NULL,'PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.collection_request VALUES ('request-1',NULL,NULL,'PROCESSING',0,?)", NOW);
        FundTransaction transaction = acceptedCollectionTransaction("transaction-1", "order-1");
        transaction.publishSuccess(true, NOW.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-1", "SUCCESS", "event-1", NOW.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_order WHERE order_id='order-1'", String.class)).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_request WHERE request_id='request-1'", String.class)).isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_db.collection_order_event WHERE event_id='event-1'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 先成功一笔后一笔取消时请求收敛为成功而非取消() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-1','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-2','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-1','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-2','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.collection_order VALUES ('order-1','request-1','transaction-1','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.collection_order VALUES ('order-2','request-1','transaction-2','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.collection_request VALUES ('request-1',NULL,NULL,'PROCESSING',0,?)", NOW);
        JdbcBusinessStore store = new JdbcBusinessStore(jdbc);

        FundTransaction successTransaction = acceptedCollectionTransaction("transaction-1", "order-1");
        successTransaction.publishSuccess(true, NOW.plusSeconds(1));
        assertThat(store.finalizeTransaction(successTransaction, 0L, "xid-1", "SUCCESS", "event-1", NOW.plusSeconds(1))).isTrue();

        FundTransaction cancelledTransaction = acceptedCollectionTransaction("transaction-2", "order-2");
        cancelledTransaction.startCompensating(NOW.plusSeconds(2));
        cancelledTransaction.publishCancelled(true, NOW.plusSeconds(3));
        assertThat(store.finalizeTransaction(cancelledTransaction, 0L, "xid-2", "CANCELLED", "event-2", NOW.plusSeconds(3))).isTrue();

        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_request WHERE request_id='request-1'", String.class)).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM business_db.collection_request WHERE request_id='request-1'", String.class)).isEqualTo("transaction-1");
    }

    @Test
    void 请求已是终态时重复发布幂等跳过且不回滚交易终态() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-1','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-2','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-1','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-2','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.collection_order VALUES ('order-1','request-1','transaction-1','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.collection_order VALUES ('order-2','request-1','transaction-2','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.collection_request VALUES ('request-1',NULL,NULL,'PROCESSING',0,?)", NOW);
        JdbcBusinessStore store = new JdbcBusinessStore(jdbc);

        FundTransaction first = acceptedCollectionTransaction("transaction-1", "order-1");
        first.publishSuccess(true, NOW.plusSeconds(1));
        assertThat(store.finalizeTransaction(first, 0L, "xid-1", "SUCCESS", "event-1", NOW.plusSeconds(1))).isTrue();
        // 第一笔成功后第二笔进入人工态：请求被冻结为 MANUAL_REVIEW，等待复核收敛
        FundTransaction second = acceptedCollectionTransaction("transaction-2", "order-2");
        second.requireManualReview(NOW.plusSeconds(2));
        assertThat(store.moveToManualReview(second, 0L, "xid-2", "event-2", "case-1", "FACT_MISMATCH", NOW.plusSeconds(2))).isTrue();
        // 人工态收敛为成功后请求到达终态；并发或重试的第二次收敛发布必须幂等跳过而非抛异常回滚
        second.resumeFromManualReview(false, NOW.plusSeconds(3));
        second.publishSuccess(true, NOW.plusSeconds(4));
        assertThat(store.finalizeTransaction(second, 1L, "xid-2", "SUCCESS", "event-3", NOW.plusSeconds(4))).isTrue();

        FundTransaction duplicate = acceptedCollectionTransaction("transaction-2", "order-2");
        duplicate.publishSuccess(true, NOW.plusSeconds(5));
        assertThatCode(() -> store.finalizeTransaction(duplicate, 0L, "xid-2", "SUCCESS", "event-4", NOW.plusSeconds(5)))
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_request WHERE request_id='request-1'", String.class)).isEqualTo("SUCCESS");
        // 请求已收敛为成功订单的交易号，重复发布不得覆盖该既成事实
        assertThat(jdbc.queryForObject("SELECT transaction_id FROM business_db.collection_request WHERE request_id='request-1'", String.class)).isEqualTo("transaction-2");
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.fund_transaction WHERE transaction_id='transaction-2'", String.class)).isEqualTo("SUCCESS");
    }

    @Test
    void 人工复核投影仍正常写入固定请求() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-1','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-1','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.collection_order VALUES ('order-1','request-1','transaction-1','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.collection_request VALUES ('request-1',NULL,NULL,'PROCESSING',0,?)", NOW);
        FundTransaction transaction = acceptedCollectionTransaction("transaction-1", "order-1");
        transaction.requireManualReview(NOW.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).moveToManualReview(transaction, 0L, "xid-1", "event-1", "case-1", "FACT_MISMATCH", NOW.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_order WHERE order_id='order-1'", String.class)).isEqualTo("MANUAL_REVIEW");
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.collection_request WHERE request_id='request-1'", String.class)).isEqualTo("MANUAL_REVIEW");
    }

    @Test
    void 二维码交易状态只能由统一交易主单投影() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource());
        createTables(jdbc);
        jdbc.update("INSERT INTO business_db.fund_transaction VALUES ('transaction-qr','PROCESSING',0,?)", NOW);
        jdbc.update("INSERT INTO business_db.tcc_global VALUES ('xid-qr','PROCESSING',?,NULL)", NOW);
        jdbc.update("INSERT INTO business_db.qr_pay_order VALUES ('qr-order-1','transaction-qr','PROCESSING',0,?)", NOW);
        FundTransaction transaction = FundTransaction.accept("transaction-qr", TransactionType.QR_PAY,
                SourceType.QR_PAY_ORDER, "qr-order-1", "payer-1", "account-payer-1", "account-payee-1",
                FundingSource.BALANCE, 5200L, "123e4567-e89b-12d3-a456-426614174001", "LOW", "0".repeat(32), NOW);
        transaction.publishSuccess(true, NOW.plusSeconds(1));

        boolean published = new JdbcBusinessStore(jdbc).finalizeTransaction(transaction, 0L, "xid-qr", "SUCCESS", "event-qr", NOW.plusSeconds(1));

        assertThat(published).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM business_db.qr_pay_order WHERE qr_order_id='qr-order-1'", String.class)).isEqualTo("SUCCESS");
    }

    /** 构造固定请求来源的已受理交易，幂等键各不相同以匹配多笔独立受理。 */
    private static FundTransaction acceptedCollectionTransaction(String transactionId, String orderId) {
        return FundTransaction.accept(transactionId, TransactionType.TRANSFER,
                SourceType.COLLECTION_REQUEST_ORDER, orderId, "payer-1", "account-payer-1", "account-payee-1",
                FundingSource.BALANCE, 5200L, UUID.randomUUID().toString(), "LOW", "0".repeat(32), NOW);
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
        jdbc.execute("CREATE TABLE business_db.manual_case (case_id VARCHAR(26) PRIMARY KEY,case_type VARCHAR(32),subject_type VARCHAR(24),subject_id VARCHAR(26),transaction_id VARCHAR(26),reason_code VARCHAR(32),status VARCHAR(16),operator_id VARCHAR(26),version BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP)");
    }
}
