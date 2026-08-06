package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.RiskHistoryPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * 基于统一交易主单（fund_transaction）的风控历史事实查询实现。
 *
 * <p>历史支付以发起人标识（initiator_user_id）识别付款人，以收款账户隔离对手；
 * 查询结果只用于受理前风控评估，不触碰余额、冻结或账本事实。</p>
 */
@Repository
public class JdbcRiskHistory implements RiskHistoryPort {
    private final JdbcTemplate jdbc;

    /** 创建风控历史查询。 */
    public JdbcRiskHistory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int countRecentPayments(String payerUserId, Instant since) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM business_db.fund_transaction WHERE initiator_user_id=? AND created_at>=?",
                Integer.class, payerUserId, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    @Override
    public int countRepeatedPayments(String payerUserId, String payeeAccountId, long amountFen, Instant since) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM business_db.fund_transaction "
                        + "WHERE initiator_user_id=? AND payee_account_id=? AND amount_fen=? AND created_at>=?",
                Integer.class, payerUserId, payeeAccountId, amountFen, Timestamp.from(since));
        return count == null ? 0 : count;
    }

    @Override
    public boolean hasTradedWith(String payerUserId, String payeeAccountId) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM business_db.fund_transaction "
                        + "WHERE initiator_user_id=? AND payee_account_id=? LIMIT 1)",
                Boolean.class, payerUserId, payeeAccountId);
        return Boolean.TRUE.equals(exists);
    }
}
