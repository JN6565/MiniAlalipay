package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.RefundStore;
import com.minialalipay.business.domain.refund.RefundOrder;
import com.minialalipay.business.domain.refund.RefundOrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 基于 JDBC 的退款订单仓储。
 *
 * <p>只访问 business_db 的退款订单与幂等表；应用服务的事务将订单、幂等事实一并提交。
 * 本实现不读写账户余额、账本或资金交易表。</p>
 */
@Repository
public class JdbcRefundStore implements RefundStore {
    private static final String ORDER_COLUMNS = "refund_order_id,original_transaction_id,merchant_user_id,"
            + "merchant_account_id,payer_user_id,payer_account_id,original_business_type,funding_source,amount_fen,"
            + "reason_code,status,transaction_id,version,created_at,updated_at,completed_at";

    private final JdbcTemplate jdbc;

    /** 创建 JDBC 退款仓储。 */
    public JdbcRefundStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RefundOrder> findById(String refundOrderId) {
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM business_db.refund_order WHERE refund_order_id=?",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), refundOrderId);
    }

    @Override
    public Optional<RefundOrder> findByOriginalTransactionId(String originalTransactionId) {
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM business_db.refund_order WHERE original_transaction_id=?",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), originalTransactionId);
    }

    @Override
    public List<RefundOrder> findByMerchantUserId(String merchantUserId, String status, int limit) {
        if (status == null || status.isBlank()) {
            return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM business_db.refund_order WHERE merchant_user_id=? ORDER BY created_at DESC LIMIT ?",
                    (rs, row) -> map(rs), merchantUserId, limit);
        }
        return jdbc.query("SELECT " + ORDER_COLUMNS + " FROM business_db.refund_order WHERE merchant_user_id=? AND status=? ORDER BY created_at DESC LIMIT ?",
                (rs, row) -> map(rs), merchantUserId, status, limit);
    }

    @Override
    public boolean create(RefundOrder order) {
        return jdbc.update("INSERT INTO business_db.refund_order "
                        + "(refund_order_id,original_transaction_id,merchant_user_id,merchant_account_id,"
                        + "payer_user_id,payer_account_id,original_business_type,funding_source,amount_fen,"
                        + "reason_code,status,transaction_id,version,created_at,updated_at,completed_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                order.getRefundOrderId(), order.getOriginalTransactionId(), order.getMerchantUserId(),
                order.getMerchantAccountId(), order.getPayerUserId(), order.getPayerAccountId(),
                order.getOriginalBusinessType(), order.getFundingSource(), order.getAmountFen(),
                order.getReasonCode(), order.getStatus().name(), order.getTransactionId(), order.getVersion(),
                Timestamp.from(order.getCreatedAt()), Timestamp.from(order.getUpdatedAt()),
                order.getCompletedAt() == null ? null : Timestamp.from(order.getCompletedAt())) == 1;
    }

    @Override
    public boolean update(RefundOrder order, long expectedVersion) {
        return jdbc.update("UPDATE business_db.refund_order SET status=?,transaction_id=?,version=?,updated_at=? "
                        + "WHERE refund_order_id=? AND version=?",
                order.getStatus().name(), order.getTransactionId(), order.getVersion(),
                Timestamp.from(order.getUpdatedAt()), order.getRefundOrderId(), expectedVersion) == 1;
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String idempotencyKey) {
        return jdbc.query("SELECT request_digest,resource_id FROM business_db.idempotency_record "
                        + "WHERE principal_key=? AND api_scope=? AND idempotency_key=?",
                rs -> rs.next() ? Optional.of(new IdempotencyRecord(rs.getBytes("request_digest"),
                        rs.getString("resource_id"))) : Optional.empty(),
                principal, operation, idempotencyKey);
    }

    @Override
    public boolean reserveIdempotency(String recordId, String principal, String operation, String idempotencyKey,
                                      byte[] requestDigest, String resourceId) {
        return jdbc.update("INSERT IGNORE INTO business_db.idempotency_record "
                        + "(record_id,principal_key,api_scope,idempotency_key,request_digest,resource_type,resource_id,status,expires_at,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?, 'REFUND_ORDER', ?, 'PROCESSING', DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
                recordId, principal, operation, idempotencyKey, requestDigest, resourceId) == 1;
    }

    private RefundOrder map(ResultSet rs) throws SQLException {
        Timestamp completed = rs.getTimestamp("completed_at");
        return new RefundOrder(rs.getString("refund_order_id"), rs.getString("original_transaction_id"),
                rs.getString("merchant_user_id"), rs.getString("merchant_account_id"),
                rs.getString("payer_user_id"), rs.getString("payer_account_id"),
                rs.getString("original_business_type"), rs.getString("funding_source"), rs.getLong("amount_fen"),
                rs.getString("reason_code"), RefundOrderStatus.valueOf(rs.getString("status")),
                rs.getString("transaction_id"), rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                completed == null ? null : completed.toInstant());
    }
}
