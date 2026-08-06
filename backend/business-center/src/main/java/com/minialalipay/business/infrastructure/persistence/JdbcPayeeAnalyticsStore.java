package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.PayeeAnalyticsStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 基于统一交易主单（fund_transaction）的收款分析投影查询实现。
 *
 * <p>收款只统计本人作为收款方且已到确定终态（SUCCESS）的 QR_PAY/CREDIT_PAY 交易，
 * 按来源订单去重；退款统计本人作为原收款方发出的 SUCCESS REFUND 交易。
 * 索引 idx_fund_transaction_payee(payee_account_id,created_at) 支撑收款维度查询。</p>
 */
@Repository
public class JdbcPayeeAnalyticsStore implements PayeeAnalyticsStore {
    private final JdbcTemplate jdbc;

    /** 创建收款分析投影查询。 */
    public JdbcPayeeAnalyticsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PayeeAnalytics analytics(String payeeAccountId, Instant since, Instant now) {
        List<PaymentMethodStat> methods = jdbc.query(
                "SELECT business_type, COUNT(DISTINCT source_order_id) AS order_count, "
                        + "COUNT(*) AS transaction_count, COALESCE(SUM(amount_fen),0) AS amount_fen "
                        + "FROM business_db.fund_transaction "
                        + "WHERE payee_account_id=? AND status='SUCCESS' "
                        + "AND business_type IN ('QR_PAY','CREDIT_PAY') AND created_at>=? "
                        + "GROUP BY business_type ORDER BY business_type",
                (rs, row) -> {
                    long orderCount = rs.getLong("order_count");
                    long transactionCount = rs.getLong("transaction_count");
                    long amountFen = rs.getLong("amount_fen");
                    return new PaymentMethodStat(rs.getString("business_type"), orderCount, amountFen);
                },
                payeeAccountId, Timestamp.from(since));

        long orderCount = methods.stream().mapToLong(PaymentMethodStat::orderCount).sum();
        Long transactionCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM business_db.fund_transaction "
                        + "WHERE payee_account_id=? AND status='SUCCESS' "
                        + "AND business_type IN ('QR_PAY','CREDIT_PAY') AND created_at>=?",
                Long.class, payeeAccountId, Timestamp.from(since));
        long gross = methods.stream().mapToLong(PaymentMethodStat::amountFen).sum();
        long refund = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_fen),0) FROM business_db.fund_transaction "
                        + "WHERE payer_account_id=? AND status='SUCCESS' AND business_type='REFUND' AND created_at>=?",
                Long.class, payeeAccountId, Timestamp.from(since));
        return new PayeeAnalytics(orderCount, transactionCount == null ? 0 : transactionCount, gross,
                refund, gross - refund, methods, since, now);
    }
}
