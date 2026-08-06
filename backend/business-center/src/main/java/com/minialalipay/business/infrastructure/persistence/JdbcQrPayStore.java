package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderEvent;
import com.minialalipay.business.domain.qrpay.QrPayOrderStatus;
import com.minialalipay.business.domain.qrpay.QrTokenDigest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 动态二维码来源订单的 JDBC 仓储。
 *
 * <p>令牌表只读写 SHA-256 摘要，SQL 参数与日志中不出现原始二维码令牌；订单更新通过版本列 CAS 防止不同 H5 会话并发绑定。</p>
 */
@Repository
public class JdbcQrPayStore implements QrPayStore {
    private static final String ORDER_COLUMNS = "o.qr_order_id,o.payee_user_id,o.payee_account_id,o.payer_user_id,"
            + "o.payer_account_id,o.transaction_id,o.amount_fen,o.subject,o.status,o.version,o.expires_at,o.created_at,o.updated_at,"
            + "t.token_digest,t.h5_session_id";
    private static final String ORDER_FROM = " FROM business_db.qr_pay_order o "
            + "JOIN business_db.qr_pay_token t ON t.qr_order_id=o.qr_order_id ";

    private final JdbcTemplate jdbc;

    /** 创建二维码 JDBC 仓储。 */
    public JdbcQrPayStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<QrPayOrder> findById(String orderId) {
        return jdbc.query("SELECT " + ORDER_COLUMNS + ORDER_FROM + "WHERE o.qr_order_id=?", rs ->
                rs.next() ? Optional.of(mapOrder(rs)) : Optional.empty(), orderId);
    }

    @Override
    public Optional<QrPayOrder> findByTokenDigest(byte[] tokenDigest) {
        return jdbc.query("SELECT " + ORDER_COLUMNS + ORDER_FROM + "WHERE t.token_digest=?", rs ->
                rs.next() ? Optional.of(mapOrder(rs)) : Optional.empty(), tokenDigest);
    }

    @Override
    public List<QrPayOrder> findByPayeeUserId(String payeeUserId, String status, int limit) {
        String sql = "SELECT " + ORDER_COLUMNS + ORDER_FROM + "WHERE o.payee_user_id=? "
                + (status == null || status.isBlank() ? "" : "AND o.status=? ")
                + "ORDER BY o.created_at DESC LIMIT ?";
        Object[] arguments = status == null || status.isBlank()
                ? new Object[]{payeeUserId, limit} : new Object[]{payeeUserId, status, limit};
        return jdbc.query(sql, (rs, rowNum) -> mapOrder(rs), arguments);
    }

    @Override
    public boolean create(QrPayOrder order, byte[] tokenDigest, String recordId, String userId,
                          String idempotencyKey, byte[] requestDigest) {
        if (jdbc.update("INSERT IGNORE INTO business_db.idempotency_record "
                        + "(record_id,principal_key,api_scope,idempotency_key,request_digest,resource_type,resource_id,status,expires_at,created_at,updated_at) "
                        + "VALUES (?,?, 'CREATE_QR_PAY', ?, ?, 'QR_PAY_ORDER', ?, 'PROCESSING', DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
                recordId, userId, idempotencyKey, requestDigest, order.getOrderId()) != 1) {
            return false;
        }
        jdbc.update("INSERT INTO business_db.qr_pay_order "
                        + "(qr_order_id,payee_user_id,payee_account_id,amount_fen,subject,status,version,expires_at,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                order.getOrderId(), order.getPayeeUserId(), order.getPayeeAccountId(), order.getAmountFen(), order.getSubject(),
                order.getStatus().name(), order.getVersion(), Timestamp.from(order.getExpiresAt()), Timestamp.from(order.getCreatedAt()),
                Timestamp.from(order.getUpdatedAt()));
        jdbc.update("INSERT INTO business_db.qr_pay_token "
                        + "(token_digest,qr_order_id,status,expires_at,created_at) VALUES (?,?,'ACTIVE',?,?)",
                tokenDigest, order.getOrderId(), Timestamp.from(order.getExpiresAt()), Timestamp.from(order.getCreatedAt()));
        return true;
    }

    @Override
    public boolean update(QrPayOrder order, long expectedVersion) {
        int changed = jdbc.update("UPDATE business_db.qr_pay_order SET payer_user_id=?,payer_account_id=?,transaction_id=?,status=?,version=?,updated_at=? "
                        + "WHERE qr_order_id=? AND version=?",
                order.getPayerUserId(), order.getPayerAccountId(), order.getTransactionId(), order.getStatus().name(), order.getVersion(),
                Timestamp.from(order.getUpdatedAt()), order.getOrderId(), expectedVersion);
        if (changed != 1) return false;
        String sessionId = order.getBoundBootstrapSessionId();
        jdbc.update("UPDATE business_db.qr_pay_token SET h5_session_id=CASE WHEN ? IS NULL THEN h5_session_id ELSE ? END, "
                        + "bootstrap_session_hash=CASE WHEN ? IS NULL THEN bootstrap_session_hash ELSE UNHEX(SHA2(?,256)) END, "
                        + "status=CASE WHEN ? IS NULL THEN status ELSE 'BOUND' END, "
                        + "consumed_at=CASE WHEN ? IS NULL THEN consumed_at ELSE COALESCE(consumed_at, ?) END "
                        + "WHERE qr_order_id=?",
                sessionId, sessionId, sessionId, sessionId, sessionId, sessionId, Timestamp.from(order.getUpdatedAt()), order.getOrderId());
        return true;
    }

    @Override
    public void appendOrderEvent(QrPayOrderEvent event) {
        jdbc.update("INSERT IGNORE INTO business_db.qr_pay_order_event "
                        + "(event_id,qr_order_id,transaction_id,status,occurred_at,retention_until) VALUES (?,?,?,?,?,?)",
                event.eventId(), event.qrOrderId(), event.transactionId(), event.status(), Timestamp.from(event.occurredAt()),
                Timestamp.from(event.occurredAt().plusSeconds(7 * 24 * 60 * 60)));
    }

    @Override
    public List<QrPayOrderEvent> findOrderEventsAfter(String orderId, String lastEventId, int limit) {
        return jdbc.query("SELECT event_id,qr_order_id,transaction_id,status,occurred_at FROM business_db.qr_pay_order_event "
                        + "WHERE qr_order_id=? AND retention_until>=UTC_TIMESTAMP(3) AND (occurred_at > "
                        + "(SELECT occurred_at FROM business_db.qr_pay_order_event WHERE qr_order_id=? AND event_id=?) "
                        + "OR (occurred_at=(SELECT occurred_at FROM business_db.qr_pay_order_event WHERE qr_order_id=? AND event_id=?) AND event_id>?)) "
                        + "ORDER BY occurred_at,event_id LIMIT ?",
                (rs, rowNum) -> mapEvent(rs), orderId, orderId, lastEventId, orderId, lastEventId, lastEventId, limit);
    }

    @Override
    public Optional<QrPayOrderEvent> findOrderEvent(String orderId, String eventId) {
        return jdbc.query("SELECT event_id,qr_order_id,transaction_id,status,occurred_at FROM business_db.qr_pay_order_event "
                        + "WHERE qr_order_id=? AND event_id=? AND retention_until>=UTC_TIMESTAMP(3)",
                rs -> rs.next() ? Optional.of(mapEvent(rs)) : Optional.empty(), orderId, eventId);
    }

    @Override
    public Optional<QrPayOrderEvent> findLatestOrderEvent(String orderId) {
        return jdbc.query("SELECT event_id,qr_order_id,transaction_id,status,occurred_at FROM business_db.qr_pay_order_event "
                        + "WHERE qr_order_id=? AND retention_until>=UTC_TIMESTAMP(3) ORDER BY occurred_at DESC,event_id DESC LIMIT 1",
                rs -> rs.next() ? Optional.of(mapEvent(rs)) : Optional.empty(), orderId);
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String idempotencyKey) {
        return jdbc.query("SELECT request_digest,resource_id FROM business_db.idempotency_record "
                        + "WHERE principal_key=? AND api_scope=? AND idempotency_key=?",
                rs -> rs.next() ? Optional.of(new IdempotencyRecord(rs.getBytes("request_digest"), rs.getString("resource_id")))
                        : Optional.empty(), principal, operation, idempotencyKey);
    }

    @Override
    public boolean reserveIdempotency(String recordId, String principal, String operation, String idempotencyKey,
                                      byte[] requestDigest, String orderId) {
        try {
            return jdbc.update("INSERT INTO business_db.idempotency_record "
                            + "(record_id,principal_key,api_scope,idempotency_key,request_digest,resource_type,resource_id,status,expires_at,created_at,updated_at) "
                            + "VALUES (?,?,?,?,?,'QR_PAY_ORDER',?,'PROCESSING',DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                    recordId, principal, operation, idempotencyKey, requestDigest, orderId) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    private static QrPayOrder mapOrder(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QrPayOrder(rs.getString("qr_order_id"), rs.getString("payee_user_id"), rs.getString("payee_account_id"),
                rs.getLong("amount_fen"), rs.getString("subject"),
                QrTokenDigest.fromHex(java.util.HexFormat.of().formatHex(rs.getBytes("token_digest"))),
                QrPayOrderStatus.valueOf(rs.getString("status")), rs.getString("h5_session_id"),
                rs.getString("payer_user_id"), rs.getString("payer_account_id"), rs.getString("transaction_id"), rs.getLong("version"),
                rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static QrPayOrderEvent mapEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new QrPayOrderEvent(rs.getString("event_id"), rs.getString("qr_order_id"), rs.getString("transaction_id"),
                rs.getString("status"), rs.getTimestamp("occurred_at").toInstant());
    }
}
