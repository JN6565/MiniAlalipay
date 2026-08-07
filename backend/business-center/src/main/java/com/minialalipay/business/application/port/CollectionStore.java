package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.collection.CollectionRequest;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionOrderEvent;
import com.minialalipay.business.domain.collection.PersonalCollectionCode;

import java.util.List;
import java.util.Optional;

/**
 * 个人码与固定收款请求的非资金持久化端口。
 *
 * <p>实现只能操作 personal_collection_code、collection_request 和幂等记录；不得创建交易、修改账户或账本。</p>
 */
public interface CollectionStore {
    /** 查询用户当前有效个人码。 */
    Optional<PersonalCollectionCode> findActiveCode(String userId);

    /** 按个人码标识读取历史或当前个人码，用于幂等回放。 */
    Optional<PersonalCollectionCode> findCode(String codeId);

    /** 按仅可持久化的个人码令牌摘要查询当前有效码。 */
    Optional<PersonalCollectionCode> findActiveCodeByTokenDigest(byte[] tokenDigest);

    /** 原子替换当前有效个人码；首次生成时 oldCode 为 null。 */
    boolean replaceCode(PersonalCollectionCode oldCode, PersonalCollectionCode newCode, byte[] tokenDigest,
                        String recordId, String userId, String idempotencyKey, byte[] requestDigest);

    /** 使用版本 CAS 停用个人码。 */
    boolean updateCode(PersonalCollectionCode code, long expectedVersion);

    /** 按请求标识读取固定收款请求。 */
    Optional<CollectionRequest> findRequest(String requestId);

    /** 按仅可持久化的固定请求令牌摘要查询请求。 */
    Optional<CollectionRequest> findRequestByTokenDigest(byte[] tokenDigest);

    /** 读取已绑定同一 H5 会话的 C2C 订单，用于令牌交换幂等恢复。 */
    Optional<CollectionOrder> findOrderByBootstrapSessionId(String bootstrapSessionId);

    /** 按订单标识读取 C2C 来源订单，用于对象级授权和确认、付款。 */
    Optional<CollectionOrder> findOrder(String orderId);

    /** 创建个人码付款订单并绑定 H5 会话。 */
    boolean createPersonalOrder(CollectionOrder order, String bootstrapSessionId);

    /** 在同一事务中 CAS 占用固定请求并创建绑定 H5 会话的订单。 */
    boolean reserveRequestAndCreateOrder(CollectionRequest request, long requestExpectedVersion,
                                         CollectionOrder order, String bootstrapSessionId);

    /** 按版本 CAS 更新 C2C 订单。 */
    boolean updateOrder(CollectionOrder order, long expectedVersion);

    /** 清除终态订单的 H5 会话绑定，允许同一会话创建新订单。 */
    void clearSessionBinding(String orderId);

    /**
     * 原子受理 C2C 订单，并为固定请求同步写入 PROCESSING 投影和可重放事件。
     *
     * <p>调用方必须已消费确认令牌；实现必须在同一 {@code business_db} 事务中完成订单 CAS、
     * 请求投影和事件写入，避免 SSE 观察到没有来源订单的虚假处理中状态。</p>
     */
    default boolean acceptOrderForPayment(CollectionOrder order, long expectedVersion, CollectionOrderEvent event) {
        return updateOrder(order, expectedVersion);
    }

    /** 写入固定请求的最小公开状态事件；实现必须与对应状态变更同属本地事务。 */
    default void appendRequestEvent(CollectionOrderEvent event) {
        throw new UnsupportedOperationException("当前存储未实现 C2C SSE 事件写入");
    }

    /** 按固定请求和游标读取保留期内的后续公开事件。 */
    default List<CollectionOrderEvent> findRequestEventsAfter(String requestId, String lastEventId, int limit) {
        throw new UnsupportedOperationException("当前存储未实现 C2C SSE 事件读取");
    }

    /** 查找固定请求保留期内的指定游标，用于识别无法补发的断线续传。 */
    default Optional<CollectionOrderEvent> findRequestEvent(String requestId, String eventId) {
        throw new UnsupportedOperationException("当前存储未实现 C2C SSE 游标读取");
    }

    /** 获取固定请求的最后一个持久化公开事件，首次订阅以它发送当前权威快照。 */
    default Optional<CollectionOrderEvent> findLatestRequestEvent(String requestId) {
        throw new UnsupportedOperationException("当前存储未实现 C2C SSE 快照读取");
    }

    /** 在同一事务中保存固定请求、令牌摘要与创建幂等记录。 */
    boolean createRequest(CollectionRequest request, byte[] tokenDigest, String recordId, String userId,
                          String idempotencyKey, byte[] requestDigest);

    /** 使用版本 CAS 更新固定请求。 */
    boolean updateRequest(CollectionRequest request, long expectedVersion);

    /** 查询场景写操作的幂等事实。 */
    Optional<IdempotencyRecord> findIdempotency(String principal, String operation, String idempotencyKey);

    /** 预占非创建写操作的幂等事实。 */
    boolean reserveIdempotency(String recordId, String principal, String operation, String idempotencyKey,
                               byte[] requestDigest, String resourceId, String resourceType);

    /** 幂等事实只暴露请求摘要与资源标识。 */
    record IdempotencyRecord(byte[] requestDigest, String resourceId) { }
}
