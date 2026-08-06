package com.minialalipay.business.infrastructure.persistence;

import com.minialalipay.business.application.port.RechargeStore;
import com.minialalipay.business.domain.recharge.RechargeDailyUsage;
import com.minialalipay.business.domain.recharge.RechargeOrder;
import com.minialalipay.business.domain.recharge.RechargeOrderStatus;
import com.minialalipay.business.domain.recharge.RechargePolicy;
import com.minialalipay.business.domain.recharge.RechargePolicyStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 基于 JDBC 的充值业务仓储。
 *
 * <p>所有方法仅访问 business_db 的充值、日额度和幂等表。应用服务的事务将日额度 CAS、订单和幂等事实
 * 一并提交；本实现不读写账户余额、账本或资金交易表。</p>
 */
@Repository
public class JdbcRechargeStore implements RechargeStore {
    private final JdbcTemplate jdbc;

    /** 创建 JDBC 充值仓储。 */
    public JdbcRechargeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public RechargePolicy getActivePolicy() {
        return jdbc.query("SELECT policy_id,single_limit_fen,daily_limit_fen,daily_count_limit,status,version,effective_at "
                        + "FROM business_db.recharge_policy WHERE status='ACTIVE' ORDER BY effective_at DESC LIMIT 1",
                rs -> rs.next() ? new RechargePolicy(rs.getString("policy_id"), rs.getLong("single_limit_fen"),
                        rs.getLong("daily_limit_fen"), rs.getInt("daily_count_limit"),
                        RechargePolicyStatus.valueOf(rs.getString("status")), rs.getLong("version"),
                        rs.getTimestamp("effective_at").toInstant()) : null);
    }

    @Override
    public Optional<RechargeDailyUsage> findDailyUsage(String userId, LocalDate businessDate) {
        return jdbc.query("SELECT user_id,business_date,processing_fen,success_fen,processing_count,success_count,version,updated_at "
                        + "FROM business_db.recharge_daily_usage WHERE user_id=? AND business_date=?",
                rs -> rs.next() ? Optional.of(new RechargeDailyUsage(rs.getString("user_id"),
                        rs.getDate("business_date").toLocalDate(), rs.getLong("processing_fen"),
                        rs.getLong("success_fen"), rs.getInt("processing_count"), rs.getInt("success_count"),
                        rs.getLong("version"), rs.getTimestamp("updated_at").toInstant())) : Optional.empty(),
                userId, Date.valueOf(businessDate));
    }

    @Override
    public Optional<IdempotencyRecord> findIdempotency(String userId, String idempotencyKey) {
        return jdbc.query("SELECT request_digest,resource_id FROM business_db.idempotency_record "
                        + "WHERE principal_key=? AND api_scope='CREATE_RECHARGE' AND idempotency_key=?",
                rs -> rs.next() ? Optional.of(new IdempotencyRecord(rs.getBytes("request_digest"),
                        rs.getString("resource_id"))) : Optional.empty(), userId, idempotencyKey);
    }

    @Override
    public boolean reserveIdempotency(String recordId, String userId, String idempotencyKey, byte[] requestHash,
                                      String rechargeOrderId) {
        return jdbc.update("INSERT IGNORE INTO business_db.idempotency_record "
                        + "(record_id,principal_key,api_scope,idempotency_key,request_digest,resource_type,resource_id,status,expires_at,created_at,updated_at) "
                        + "VALUES (?,?, 'CREATE_RECHARGE', ?, ?, 'RECHARGE_ORDER', ?, 'PROCESSING', DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY), UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))",
                recordId, userId, idempotencyKey, requestHash, rechargeOrderId) == 1;
    }

    @Override
    public boolean createOrderAndUpdateUsage(RechargeOrder order, RechargeDailyUsage usage, long expectedUsageVersion) {
        int affected = jdbc.update("UPDATE business_db.recharge_daily_usage SET processing_fen=?,success_fen=?,processing_count=?,success_count=?,version=?,updated_at=? "
                        + "WHERE user_id=? AND business_date=? AND version=?",
                usage.getProcessingFen(), usage.getSuccessFen(), usage.getProcessingCount(), usage.getSuccessCount(),
                usage.getVersion(), Timestamp.from(usage.getUpdatedAt()), usage.getUserId(),
                Date.valueOf(usage.getBusinessDate()), expectedUsageVersion);
        if (affected == 0 && expectedUsageVersion == 0) {
            try {
                affected = jdbc.update("INSERT INTO business_db.recharge_daily_usage "
                                + "(user_id,business_date,processing_fen,success_fen,processing_count,success_count,version,updated_at) VALUES (?,?,?,?,?,?,?,?)",
                        usage.getUserId(), Date.valueOf(usage.getBusinessDate()), usage.getProcessingFen(), usage.getSuccessFen(),
                        usage.getProcessingCount(), usage.getSuccessCount(), usage.getVersion(), Timestamp.from(usage.getUpdatedAt()));
            } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                return false;
            }
        }
        if (affected != 1) return false;
        jdbc.update("INSERT INTO business_db.recharge_order "
                        + "(recharge_order_id,user_id,target_account_id,amount_fen,business_date,policy_id,policy_version,status,version,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                order.getRechargeOrderId(), order.getUserId(), order.getTargetAccountId(), order.getAmountFen(),
                Date.valueOf(order.getBusinessDate()), order.getPolicyId(), order.getPolicyVersion(), order.getStatus().name(),
                order.getVersion(), Timestamp.from(order.getCreatedAt()), Timestamp.from(order.getUpdatedAt()));
        return true;
    }

    @Override
    public boolean updateOrder(RechargeOrder order, long expectedVersion) {
        return jdbc.update("UPDATE business_db.recharge_order SET status=?,transaction_id=?,reject_reason_code=?,version=?,updated_at=? "
                        + "WHERE recharge_order_id=? AND version=?",
                order.getStatus().name(), order.getTransactionId(), order.getRejectReasonCode(), order.getVersion(),
                Timestamp.from(order.getUpdatedAt()), order.getRechargeOrderId(), expectedVersion) == 1;
    }

    @Override
    public Optional<RechargeOrder> findOrder(String rechargeOrderId) {
        return jdbc.query("SELECT recharge_order_id,user_id,target_account_id,amount_fen,business_date,policy_id,policy_version,status,transaction_id,reject_reason_code,version,created_at,updated_at "
                        + "FROM business_db.recharge_order WHERE recharge_order_id=?",
                rs -> rs.next() ? Optional.of(new RechargeOrder(rs.getString("recharge_order_id"), rs.getString("user_id"),
                        rs.getString("target_account_id"), rs.getLong("amount_fen"), rs.getDate("business_date").toLocalDate(),
                        rs.getString("policy_id"), rs.getLong("policy_version"), RechargeOrderStatus.valueOf(rs.getString("status")),
                        rs.getString("transaction_id"), rs.getString("reject_reason_code"), rs.getLong("version"),
                        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant())) : Optional.empty(),
                rechargeOrderId);
    }
}
