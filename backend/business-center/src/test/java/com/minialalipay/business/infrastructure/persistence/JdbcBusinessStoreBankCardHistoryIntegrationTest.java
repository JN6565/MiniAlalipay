package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.BusinessStore;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 银行卡流水查询的 H2 集成测试。
 *
 * <p>口径：本人指定银行卡关联的充值/提现与银行卡出资的转账/扫码支付均可见；
 * 余额出资且无银行卡关联的交易、他人交易不得出现在卡流水中。</p>
 */
class JdbcBusinessStoreBankCardHistoryIntegrationTest {
    private static final String USER = "USR00000000000000000000001";
    private static final String CARD = "CARD0000000000000000000001";

    private JdbcTemplate jdbc;
    private JdbcBusinessStore store;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:bankcard_history_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS business_db");
        jdbc.execute("CREATE TABLE business_db.fund_transaction (transaction_id VARCHAR(26) PRIMARY KEY,"
                + "business_type VARCHAR(32),source_type VARCHAR(32),source_order_id VARCHAR(26),"
                + "initiator_user_id VARCHAR(26),payer_account_id VARCHAR(26),payee_account_id VARCHAR(26),"
                + "funding_source VARCHAR(16),related_transaction_id VARCHAR(26),bank_card_id VARCHAR(26),"
                + "amount_fen BIGINT,idempotency_key VARCHAR(64),status VARCHAR(32),risk_level VARCHAR(16),"
                + "trace_id VARCHAR(32),version BIGINT,created_at TIMESTAMP(3),updated_at TIMESTAMP(3))");
        store = new JdbcBusinessStore(jdbc);
    }

    private void insert(String txId, String businessType, String initiator, String cardId,
                        String fundingSource, Instant createdAt) {
        jdbc.update("INSERT INTO business_db.fund_transaction (transaction_id,business_type,source_type,"
                        + "source_order_id,initiator_user_id,payer_account_id,payee_account_id,funding_source,"
                        + "related_transaction_id,bank_card_id,amount_fen,idempotency_key,status,risk_level,"
                        + "trace_id,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                txId, businessType, "TRANSFER_DRAFT", txId, initiator, null, "ACC00000000000000000000002",
                fundingSource, null, cardId, 1000L, "key-" + txId, "SUCCESS", "LOW",
                "0123456789abcdef0123456789abcdef", 1L,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    /** 银行卡出资的转账/扫码支付不写账本分录，必须在卡流水中可见，否则 C 端账单双双缺失。 */
    @Test
    void bankCardFundedTransferAndQrPayAreVisible() {
        insert("TXBC0000000000000000000001", "TRANSFER", USER, CARD, "BANK_CARD",
                Instant.parse("2026-08-11T06:00:00Z"));
        insert("TXBC0000000000000000000002", "QR_PAY", USER, CARD, "BANK_CARD",
                Instant.parse("2026-08-11T07:00:00Z"));

        List<BusinessStore.FundTransactionRecord> records = store.findBankCardTransactions(USER, CARD, 20);

        assertThat(records).extracting(r -> r.transaction().getTransactionId())
                .containsExactly("TXBC0000000000000000000002", "TXBC0000000000000000000001");
    }

    /** 充值/提现保持可见；余额出资且未关联银行卡的交易不属于卡流水。 */
    @Test
    void rechargeWithdrawVisibleButBalanceTransferHidden() {
        insert("TXBC0000000000000000000003", "BANK_CARD_RECHARGE", USER, CARD, "BANK_CARD",
                Instant.parse("2026-08-11T05:00:00Z"));
        insert("TXBC0000000000000000000004", "BANK_CARD_WITHDRAW", USER, CARD, "BANK_CARD",
                Instant.parse("2026-08-11T05:30:00Z"));
        insert("TXBL0000000000000000000005", "TRANSFER", USER, null, "BALANCE",
                Instant.parse("2026-08-11T06:00:00Z"));

        List<BusinessStore.FundTransactionRecord> records = store.findBankCardTransactions(USER, CARD, 20);

        assertThat(records).extracting(r -> r.transaction().getTransactionId())
                .containsExactly("TXBC0000000000000000000004", "TXBC0000000000000000000003");
    }

    /** 卡流水按发起人与银行卡双重隔离，他人交易不得泄露。 */
    @Test
    void otherUserTransactionsAreIsolated() {
        insert("TXBC0000000000000000000006", "TRANSFER", "USR00000000000000000000099", CARD,
                "BANK_CARD", Instant.parse("2026-08-11T06:00:00Z"));

        List<BusinessStore.FundTransactionRecord> records = store.findBankCardTransactions(USER, CARD, 20);

        assertThat(records).isEmpty();
    }
}
