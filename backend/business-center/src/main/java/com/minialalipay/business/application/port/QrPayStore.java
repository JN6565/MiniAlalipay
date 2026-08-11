package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 动态二维码非资金来源订单的持久化端口。
 *
 * <p>实现只能读写 business_db 中的二维码订单、令牌摘要和幂等记录；不得创建资金交易、余额、冻结或账本事实。</p>
 */
public interface QrPayStore {
    /** 按订单标识读取二维码订单。 */
    Optional<QrPayOrder> findById(String orderId);

    /** 按原始令牌的 SHA-256 摘要读取二维码订单。 */
    Optional<QrPayOrder> findByTokenDigest(byte[] tokenDigest);

    /** 查询收款人本人创建的订单，最多返回指定数量。 */
    List<QrPayOrder> findByPayeeUserId(String payeeUserId, String status, int limit);

    /** 在同一业务库事务中保存新订单、令牌摘要和创建幂等事实。 */
    boolean create(QrPayOrder order, byte[] tokenDigest, String recordId, String userId,
                   String idempotencyKey, byte[] requestDigest);

    /** 使用传入版本执行订单与令牌会话绑定的 CAS 更新。 */
    boolean update(QrPayOrder order, long expectedVersion);

    /** 同一业务事务写入二维码订单最小公开状态事件，供 SSE 断线重放。 */
    default void appendOrderEvent(QrPayOrderEvent event) {
        throw new UnsupportedOperationException("当前存储未实现二维码 SSE 事件写入");
    }

    /** 读取保留期内指定游标之后的二维码订单事件。 */
    default List<QrPayOrderEvent> findOrderEventsAfter(String orderId, String lastEventId, int limit) {
        throw new UnsupportedOperationException("当前存储未实现二维码 SSE 事件读取");
    }

    /** 验证指定游标仍属于订单且处于重放保留期。 */
    default Optional<QrPayOrderEvent> findOrderEvent(String orderId, String eventId) {
        throw new UnsupportedOperationException("当前存储未实现二维码 SSE 游标读取");
    }

    /** 读取保留期内最新事件，用于首次订阅时发送权威快照。 */
    default Optional<QrPayOrderEvent> findLatestOrderEvent(String orderId) {
        throw new UnsupportedOperationException("当前存储未实现二维码 SSE 快照读取");
    }

    /** 查询指定操作的幂等事实。 */
    Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String idempotencyKey);

    /** 为非创建写操作占位幂等事实，冲突时返回 false。 */
    boolean reserveIdempotency(String recordId, String principal, String operation, String idempotencyKey,
                               byte[] requestDigest, String orderId);

    /** 幂等记录只暴露请求摘要和来源订单标识。 */
    record IdempotencyRecord(byte[] requestDigest, String orderId) { }

    /** 按手动输入短码查询二维码订单。 */
    default Optional<QrPayOrder> findByShortCode(String shortCode) { return Optional.empty(); }

    /** 为二维码订单分配短码；短码唯一冲突返回 false。 */
    default boolean assignShortCode(String orderId, String shortCode) { return true; }

    /** 清理过期或终态订单占用的短码，释放给新码复用。 */
    default void clearExpiredShortCodes(Instant now) { }
}
