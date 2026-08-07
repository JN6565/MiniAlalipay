package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionOrderEvent;
import com.minialalipay.business.domain.collection.CollectionOrderStatus;
import com.minialalipay.business.domain.collection.CollectionRequestStatus;
import com.minialalipay.business.domain.collection.PersonalCollectionCode;
import com.minialalipay.business.domain.collection.PersonalCollectionCodeStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * 个人码和固定收款请求 JDBC 仓储。
 *
 * <p>个人码换码在调用方事务内先 CAS 撤销旧活动码，再插入新码；活动码生成列唯一键是并发下的最终约束。所有令牌参数均为摘要字节。</p>
 */
@Repository
public class JdbcCollectionStore implements CollectionStore {
    private final JdbcTemplate jdbc;

    /** 创建 C2C 场景 JDBC 仓储。 */
    public JdbcCollectionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PersonalCollectionCode> findActiveCode(String userId) {
        return jdbc.query("SELECT code_id,owner_user_id,payee_account_id,status,version,created_at,updated_at "
                        + "FROM business_db.personal_collection_code WHERE owner_user_id=? AND status='ACTIVE'",
                rs -> rs.next() ? Optional.of(new PersonalCollectionCode(rs.getString("code_id"), rs.getString("owner_user_id"),
                        rs.getString("payee_account_id"), PersonalCollectionCodeStatus.valueOf(rs.getString("status")),
                        rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()))
                        : Optional.empty(), userId);
    }

    @Override
    public Optional<PersonalCollectionCode> findCode(String codeId) {
        return jdbc.query("SELECT code_id,owner_user_id,payee_account_id,status,version,created_at,updated_at "
                        + "FROM business_db.personal_collection_code WHERE code_id=?", rs -> rs.next()
                        ? Optional.of(new PersonalCollectionCode(rs.getString("code_id"), rs.getString("owner_user_id"),
                        rs.getString("payee_account_id"), PersonalCollectionCodeStatus.valueOf(rs.getString("status")),
                        rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()))
                        : Optional.empty(), codeId);
    }

    @Override
    public Optional<PersonalCollectionCode> findActiveCodeByTokenDigest(byte[] tokenDigest) {
        return jdbc.query("SELECT code_id,owner_user_id,payee_account_id,status,version,created_at,updated_at "
                        + "FROM business_db.personal_collection_code WHERE token_digest=? AND status='ACTIVE'", rs -> rs.next()
                        ? Optional.of(new PersonalCollectionCode(rs.getString("code_id"), rs.getString("owner_user_id"),
                        rs.getString("payee_account_id"), PersonalCollectionCodeStatus.valueOf(rs.getString("status")),
                        rs.getLong("version"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()))
                        : Optional.empty(), tokenDigest);
    }

    @Override
    public boolean replaceCode(PersonalCollectionCode oldCode, PersonalCollectionCode newCode, byte[] tokenDigest,
                               String recordId, String userId, String idempotencyKey, byte[] requestDigest) {
        if (jdbc.update("INSERT IGNORE INTO business_db.idempotency_record "
                        + "(record_id,principal_key,api_scope,idempotency_key,request_digest,resource_type,resource_id,status,expires_at,created_at,updated_at) "
                        + "VALUES (?,?,'REGENERATE_COLLECTION_CODE',? ,?,'PERSONAL_COLLECTION_CODE',?,'PROCESSING',DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                recordId, userId, idempotencyKey, requestDigest, newCode.getCodeId()) != 1) return false;
        if (oldCode != null && jdbc.update("UPDATE business_db.personal_collection_code SET status=?,version=?,updated_at=?,revoked_at=? "
                        + "WHERE code_id=? AND owner_user_id=? AND status='ACTIVE' AND version=?",
                oldCode.getStatus().name(), oldCode.getVersion(), Timestamp.from(oldCode.getUpdatedAt()), Timestamp.from(oldCode.getUpdatedAt()),
                oldCode.getCodeId(), userId, oldCode.getVersion() - 1) != 1) return false;
        try {
            jdbc.update("INSERT INTO business_db.personal_collection_code "
                            + "(code_id,owner_user_id,payee_account_id,token_digest,status,version,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                    newCode.getCodeId(), newCode.getUserId(), newCode.getAccountId(), tokenDigest, newCode.getStatus().name(),
                    newCode.getVersion(), Timestamp.from(newCode.getCreatedAt()), Timestamp.from(newCode.getUpdatedAt()));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Override
    public boolean updateCode(PersonalCollectionCode code, long expectedVersion) {
        return jdbc.update("UPDATE business_db.personal_collection_code SET status=?,version=?,updated_at=?,revoked_at=? "
                        + "WHERE code_id=? AND status='ACTIVE' AND version=?",
                code.getStatus().name(), code.getVersion(), Timestamp.from(code.getUpdatedAt()), Timestamp.from(code.getUpdatedAt()),
                code.getCodeId(), expectedVersion) == 1;
    }

    @Override
    public Optional<CollectionRequest> findRequest(String requestId) {
        return jdbc.query("SELECT request_id,requester_user_id,payee_account_id,amount_fen,subject,status,active_order_id,version,expires_at,created_at,updated_at "
                        + "FROM business_db.collection_request WHERE request_id=?", rs -> rs.next()
                        ? Optional.of(new CollectionRequest(rs.getString("request_id"), rs.getString("requester_user_id"),
                        rs.getString("payee_account_id"), rs.getLong("amount_fen"), rs.getString("subject"),
                        CollectionRequestStatus.valueOf(rs.getString("status")), rs.getString("active_order_id"), rs.getLong("version"),
                        rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()))
                        : Optional.empty(), requestId);
    }

    @Override
    public Optional<CollectionRequest> findRequestByTokenDigest(byte[] tokenDigest) {
        return jdbc.query("SELECT request_id,requester_user_id,payee_account_id,amount_fen,subject,status,active_order_id,version,expires_at,created_at,updated_at "
                        + "FROM business_db.collection_request WHERE token_digest=?", rs -> rs.next()
                        ? Optional.of(mapRequest(rs)) : Optional.empty(), tokenDigest);
    }

    @Override
    public Optional<CollectionOrder> findOrderByBootstrapSessionId(String bootstrapSessionId) {
        return jdbc.query("SELECT order_id,mode,code_id,request_id,payer_user_id,payer_account_id,payee_user_id,payee_account_id,amount_fen,subject,status,transaction_id,version,expires_at,created_at,updated_at "
                        + "FROM business_db.collection_order WHERE h5_session_id=?", rs -> rs.next() ? Optional.of(mapOrder(rs)) : Optional.empty(),
                bootstrapSessionId);
    }

    @Override
    public Optional<CollectionOrder> findOrder(String orderId) {
        return jdbc.query("SELECT order_id,mode,code_id,request_id,payer_user_id,payer_account_id,payee_user_id,payee_account_id,amount_fen,subject,status,transaction_id,version,expires_at,created_at,updated_at FROM business_db.collection_order WHERE order_id=?",
                rs -> rs.next() ? Optional.of(mapOrder(rs)) : Optional.empty(), orderId);
    }

    @Override
    public boolean createPersonalOrder(CollectionOrder order, String bootstrapSessionId) {
        try {
            return insertOrder(order, bootstrapSessionId) == 1;
        } catch (DuplicateKeyException duplicate) { return false; }
    }

    @Override
    public boolean reserveRequestAndCreateOrder(CollectionRequest request, long requestExpectedVersion,
                                                 CollectionOrder order, String bootstrapSessionId) {
        int reserved = jdbc.update("UPDATE business_db.collection_request SET status=?,active_order_id=?,version=?,updated_at=? WHERE request_id=? AND version=?",
                request.getStatus().name(), request.getActiveOrderId(), request.getVersion(), Timestamp.from(request.getUpdatedAt()),
                request.getRequestId(), requestExpectedVersion);
        if (reserved != 1) return false;
        try { return insertOrder(order, bootstrapSessionId) == 1; }
        catch (DuplicateKeyException duplicate) { return false; }
    }

    @Override
    public boolean updateOrder(CollectionOrder order, long expectedVersion) {
        return jdbc.update("UPDATE business_db.collection_order SET amount_fen=?,subject=?,transaction_id=?,status=?,version=?,updated_at=? WHERE order_id=? AND version=?",
                order.getAmountFen(), order.getSubject(), order.getTransactionId(), order.getStatus().name(), order.getVersion(), Timestamp.from(order.getUpdatedAt()),
                order.getOrderId(), expectedVersion) == 1;
    }

    @Override
    public void clearSessionBinding(String orderId) {
        jdbc.update("UPDATE business_db.collection_order SET h5_session_id=order_id WHERE order_id=?", orderId);
    }

    @Override
    public boolean acceptOrderForPayment(CollectionOrder order, long expectedVersion, CollectionOrderEvent event) {
        if (!updateOrder(order, expectedVersion)) return false;
        if (order.getRequestId() != null) {
            int requestChanged = jdbc.update("UPDATE business_db.collection_request SET status='PROCESSING',transaction_id=?,version=version+1,updated_at=? "
                            + "WHERE request_id=? AND active_order_id=? AND status='RESERVED'",
                    order.getTransactionId(), Timestamp.from(order.getUpdatedAt()), order.getRequestId(), order.getOrderId());
            if (requestChanged != 1) return false;
            appendRequestEvent(event);
        }
        return true;
    }

    @Override
    public void appendRequestEvent(CollectionOrderEvent event) {
        jdbc.update("INSERT IGNORE INTO business_db.collection_order_event "
                        + "(event_id,order_id,request_id,transaction_id,status,occurred_at,retention_until) VALUES (?,?,?,?,?,?,?)",
                event.eventId(), event.activeOrderId(), event.requestId(), event.transactionId(), event.status(),
                Timestamp.from(event.occurredAt()), Timestamp.from(event.occurredAt().plusSeconds(7 * 24 * 60 * 60)));
    }

    @Override
    public List<CollectionOrderEvent> findRequestEventsAfter(String requestId, String lastEventId, int limit) {
        return jdbc.query("SELECT event_id,request_id,order_id,transaction_id,status,occurred_at FROM business_db.collection_order_event "
                        + "WHERE request_id=? AND retention_until>=UTC_TIMESTAMP(3) AND (occurred_at > "
                        + "(SELECT occurred_at FROM business_db.collection_order_event WHERE request_id=? AND event_id=?) "
                        + "OR (occurred_at = (SELECT occurred_at FROM business_db.collection_order_event WHERE request_id=? AND event_id=?) AND event_id>?)) "
                        + "ORDER BY occurred_at,event_id LIMIT ?",
                (rs, rowNum) -> mapEvent(rs), requestId, requestId, lastEventId, requestId, lastEventId, lastEventId, limit);
    }

    @Override
    public Optional<CollectionOrderEvent> findRequestEvent(String requestId, String eventId) {
        return jdbc.query("SELECT event_id,request_id,order_id,transaction_id,status,occurred_at FROM business_db.collection_order_event "
                        + "WHERE request_id=? AND event_id=? AND retention_until>=UTC_TIMESTAMP(3)",
                rs -> rs.next() ? Optional.of(mapEvent(rs)) : Optional.empty(), requestId, eventId);
    }

    @Override
    public Optional<CollectionOrderEvent> findLatestRequestEvent(String requestId) {
        return jdbc.query("SELECT event_id,request_id,order_id,transaction_id,status,occurred_at FROM business_db.collection_order_event "
                        + "WHERE request_id=? AND retention_until>=UTC_TIMESTAMP(3) ORDER BY occurred_at DESC,event_id DESC LIMIT 1",
                rs -> rs.next() ? Optional.of(mapEvent(rs)) : Optional.empty(), requestId);
    }

    @Override
    public boolean createRequest(CollectionRequest request, byte[] tokenDigest, String recordId, String userId,
                                 String idempotencyKey, byte[] requestDigest) {
        if (jdbc.update("INSERT IGNORE INTO business_db.idempotency_record "
                        + "(record_id,principal_key,api_scope,idempotency_key,request_digest,resource_type,resource_id,status,expires_at,created_at,updated_at) "
                        + "VALUES (?,?,'CREATE_COLLECTION_REQUEST',? ,?,'COLLECTION_REQUEST',?,'PROCESSING',DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                recordId, userId, idempotencyKey, requestDigest, request.getRequestId()) != 1) return false;
        jdbc.update("INSERT INTO business_db.collection_request "
                        + "(request_id,requester_user_id,payee_account_id,token_digest,amount_fen,subject,status,version,expires_at,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)", request.getRequestId(), request.getPayeeUserId(), request.getPayeeAccountId(),
                tokenDigest, request.getAmountFen(), request.getSubject(), request.getStatus().name(), request.getVersion(),
                Timestamp.from(request.getExpiresAt()), Timestamp.from(request.getCreatedAt()), Timestamp.from(request.getUpdatedAt()));
        return true;
    }

    @Override
    public boolean updateRequest(CollectionRequest request, long expectedVersion) {
        return jdbc.update("UPDATE business_db.collection_request SET status=?,active_order_id=?,version=?,updated_at=? "
                        + "WHERE request_id=? AND version=?", request.getStatus().name(), request.getActiveOrderId(), request.getVersion(),
                Timestamp.from(request.getUpdatedAt()), request.getRequestId(), expectedVersion) == 1;
    }

    private int insertOrder(CollectionOrder order, String bootstrapSessionId) {
        return jdbc.update("INSERT INTO business_db.collection_order (order_id,mode,code_id,request_id,payer_user_id,payer_account_id,payee_user_id,payee_account_id,h5_session_id,amount_fen,subject,status,version,expires_at,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                order.getOrderId(), order.getPersonalCodeId() == null ? "FIXED_REQUEST" : "PERSONAL_QR", order.getPersonalCodeId(),
                order.getRequestId(), order.getPayerUserId(), order.getPayerAccountId(), order.getPayeeUserId(), order.getPayeeAccountId(),
                bootstrapSessionId, order.getAmountFen(), order.getSubject(), order.getStatus().name(), order.getVersion(),
                Timestamp.from(order.getExpiresAt()), Timestamp.from(order.getCreatedAt()), Timestamp.from(order.getUpdatedAt()));
    }

    private static CollectionRequest mapRequest(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CollectionRequest(rs.getString("request_id"), rs.getString("requester_user_id"), rs.getString("payee_account_id"),
                rs.getLong("amount_fen"), rs.getString("subject"), CollectionRequestStatus.valueOf(rs.getString("status")),
                rs.getString("active_order_id"), rs.getLong("version"), rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static CollectionOrder mapOrder(java.sql.ResultSet rs) throws java.sql.SQLException {
        Object amountObj = rs.getObject("amount_fen");
        Long amountFen = amountObj == null ? null : ((Number) amountObj).longValue();
        Object versionObj = rs.getObject("version");
        long version = versionObj == null ? 0L : ((Number) versionObj).longValue();
        return new CollectionOrder(rs.getString("order_id"), rs.getString("code_id"), rs.getString("request_id"),
                rs.getString("payee_user_id"), rs.getString("payee_account_id"), rs.getString("payer_user_id"),
                rs.getString("payer_account_id"), amountFen, rs.getString("subject"),
                CollectionOrderStatus.valueOf(rs.getString("status")), rs.getString("transaction_id"), version, rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static CollectionOrderEvent mapEvent(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CollectionOrderEvent(rs.getString("event_id"), rs.getString("request_id"), rs.getString("order_id"),
                rs.getString("transaction_id"), rs.getString("status"), rs.getTimestamp("occurred_at").toInstant());
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String idempotencyKey) {
        return jdbc.query("SELECT request_digest,resource_id FROM business_db.idempotency_record WHERE principal_key=? AND api_scope=? AND idempotency_key=?",
                rs -> rs.next() ? Optional.of(new IdempotencyRecord(rs.getBytes("request_digest"), rs.getString("resource_id"))) : Optional.empty(),
                principal, operation, idempotencyKey);
    }

    @Override
    public boolean reserveIdempotency(String recordId, String principal, String operation, String idempotencyKey,
                                      byte[] requestDigest, String resourceId, String resourceType) {
        try {
            return jdbc.update("INSERT INTO business_db.idempotency_record "
                            + "(record_id,principal_key,api_scope,idempotency_key,request_digest,resource_type,resource_id,status,expires_at,created_at,updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,'PROCESSING',DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY),UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",
                    recordId, principal, operation, idempotencyKey, requestDigest, resourceType, resourceId) == 1;
        } catch (DuplicateKeyException duplicate) { return false; }
    }
}
