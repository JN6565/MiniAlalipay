package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.refund.RefundOrder;

import java.util.List;
import java.util.Optional;

/**
 * 受控退款来源订单的持久化端口。
 *
 * <p>实现只能读写 business_db 中的退款订单和幂等记录；不得创建资金交易、余额、冻结或账本事实。
 * 退款发起人以原收款方（merchant）用户与账户隔离本人数据。</p>
 */
public interface RefundStore {
    /** 按订单标识读取退款订单。 */
    Optional<RefundOrder> findById(String refundOrderId);

    /** 按原交易标识读取退款订单，用于防止对同一交易重复发起退款。 */
    Optional<RefundOrder> findByOriginalTransactionId(String originalTransactionId);

    /** 查询退款发起人（原收款方）本人的退款订单。 */
    List<RefundOrder> findByMerchantUserId(String merchantUserId, String status, int limit);

    /** 在同一事务中保存新退款订单；创建幂等事实由 reserveIdempotency 先行占位。 */
    boolean create(RefundOrder order);

    /** 使用传入版本执行退款订单的 CAS 更新。 */
    boolean update(RefundOrder order, long expectedVersion);

    /** 查询指定操作的幂等事实。 */
    Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String idempotencyKey);

    /** 预占非创建写操作的幂等事实。 */
    boolean reserveIdempotency(String recordId, String principal, String operation, String idempotencyKey,
                               byte[] requestDigest, String resourceId);

    /** 幂等事实只暴露请求摘要与来源订单标识。 */
    record IdempotencyRecord(byte[] requestDigest, String resourceId) { }
}
