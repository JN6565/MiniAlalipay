package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.recharge.RechargeDailyUsage;
import com.minialalipay.business.domain.recharge.RechargeOrder;
import com.minialalipay.business.domain.recharge.RechargePolicy;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 充值策略、日额度和来源订单的本地仓储端口。
 *
 * <p>写操作必须在同一个 business_db 事务中完成幂等占位、日额度 CAS 和订单插入；本端口禁止创建资金交易、
 * 修改账户余额或触发 TCC。</p>
 */
public interface RechargeStore {
    /** 查询当前唯一活动的充值策略。 */
    RechargePolicy getActivePolicy();

    /** 查询同一用户、业务日的额度事实并用于应用层 CAS。 */
    Optional<RechargeDailyUsage> findDailyUsage(String userId, LocalDate businessDate);

    /** 按创建操作的幂等键查询既有订单。 */
    Optional<IdempotencyRecord> findIdempotency(String userId, String idempotencyKey);

    /** 在本地事务内抢占幂等键；返回 false 表示已有记录。 */
    boolean reserveIdempotency(String recordId, String userId, String idempotencyKey, byte[] requestHash,
                               String rechargeOrderId);

    /** 原子保存订单及更新后的日额度，CAS 失败时返回 false。 */
    boolean createOrderAndUpdateUsage(RechargeOrder order, RechargeDailyUsage usage, long expectedUsageVersion);

    /** 查询本人充值订单。 */
    Optional<RechargeOrder> findOrder(String rechargeOrderId);

    /** 使用传入版本 CAS 持久化充值订单状态（受理资金交易或渠道拒绝）。 */
    boolean updateOrder(RechargeOrder order, long expectedVersion);

    /** 充值创建接口的幂等快照。 */
    record IdempotencyRecord(byte[] requestHash, String rechargeOrderId) { }
}
