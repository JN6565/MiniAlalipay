package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.domain.refund.RefundOrder;
import com.minialalipay.business.domain.refund.RefundOrderStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 退款订单仓储的 H2 集成测试：创建、原交易唯一与 CAS 更新。 */
class JdbcRefundStoreIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    private JdbcRefundStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:refund_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS business_db");
        jdbc.execute("CREATE TABLE business_db.refund_order (refund_order_id VARCHAR(26) PRIMARY KEY,"
                + "original_transaction_id VARCHAR(26),merchant_user_id VARCHAR(26),merchant_account_id VARCHAR(26),"
                + "payer_user_id VARCHAR(26),payer_account_id VARCHAR(26),original_business_type VARCHAR(16),"
                + "funding_source VARCHAR(16),amount_fen BIGINT,reason_code VARCHAR(32),status VARCHAR(16),"
                + "transaction_id VARCHAR(26),version BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,completed_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE business_db.idempotency_record (record_id VARCHAR(26) PRIMARY KEY,"
                + "principal_key VARCHAR(64),api_scope VARCHAR(32),idempotency_key VARCHAR(64),request_digest VARBINARY(32),"
                + "resource_type VARCHAR(32),resource_id VARCHAR(26),status VARCHAR(16),expires_at TIMESTAMP,"
                + "created_at TIMESTAMP,updated_at TIMESTAMP)");
        store = new JdbcRefundStore(jdbc);
    }

    private static RefundOrder order(String id, String originalTx) {
        return RefundOrder.create(id, originalTx, "payee-1", "account-payee-1", "payer-1", "account-payer-1",
                "QR_PAY", "BALANCE", 100, "商品质量问题", NOW);
    }

    @Test
    void 创建读取与按原交易唯一查找() {
        assertThat(store.create(order("r-1", "t-original"))).isTrue();

        RefundOrder loaded = store.findById("r-1").orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(RefundOrderStatus.CREATED);
        assertThat(loaded.getOriginalBusinessType()).isEqualTo("QR_PAY");
        assertThat(store.findByOriginalTransactionId("t-original")).isPresent();
        assertThat(store.findByOriginalTransactionId("t-other")).isEmpty();
    }

    @Test
    void CAS更新拒绝过期版本() {
        store.create(order("r-1", "t-original"));

        RefundOrder current = store.findById("r-1").orElseThrow();
        current.submit(0L, "t-refund", NOW.plusSeconds(1));
        assertThat(store.update(current, 0L)).isTrue();

        RefundOrder stale = store.findById("r-1").orElseThrow();
        assertThat(store.update(stale, 0L)).isFalse();
    }

    @Test
    void 本人退款列表按状态过滤() {
        store.create(order("r-1", "t-original"));

        assertThat(store.findByMerchantUserId("payee-1", null, 10)).hasSize(1);
        assertThat(store.findByMerchantUserId("payee-1", "PROCESSING", 10)).isEmpty();
    }
}
