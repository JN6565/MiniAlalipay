package com.minialalipay.ai.domain.agent;

import java.util.Optional;

/**
 * AI 服务幂等记录仓储端口。
 *
 * <p>实现必须保证 {@code principalKey + apiScope + idempotencyKey}
 * 在数据库层面具有唯一约束。</p>
 */
public interface IdempotencyRepository {

    /**
     * 按作用域和幂等键查找已存在记录。
     */
    Optional<IdempotencyRecord> findByScope(
            String principalKey, String apiScope, String idempotencyKey);

    /**
     * 创建 PROCESSING 状态记录，若已存在则抛稳定异常。
     */
    void insert(IdempotencyRecord record);

    /**
     * 将记录更新为 COMPLETED 并写入响应快照。
     */
    void markCompleted(String recordId, String resourceType, String resourceId,
                       String responseJson);

    /**
     * 将记录更新为 FAILED。
     */
    void markFailed(String recordId);

    /**
     * 清理超过保留期的记录。
     */
    int deleteExpiredRecords();
}
