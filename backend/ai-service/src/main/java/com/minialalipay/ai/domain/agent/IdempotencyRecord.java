package com.minialalipay.ai.domain.agent;

import java.time.Instant;

/**
 * AI 服务幂等记录。
 *
 * <p>唯一索引为 {@code (principal_key, api_scope, idempotency_key)}，
 * 防止同一主体的同一操作因重试、并发或网络超时产生额外副作用。</p>
 *
 * @param recordId       记录主键
 * @param principalKey   主体键（脱敏 userId 或会话标识）
 * @param apiScope       接口作用域（如 AGENT_MESSAGE）
 * @param idempotencyKey 幂等键
 * @param requestDigest  规范化请求摘要（SHA-256）
 * @param resourceType   完成后的资源类型
 * @param resourceId     完成后的资源 ID
 * @param responseJson   脱敏响应快照（仅 COMPLETED 时有效）
 * @param status         当前状态
 * @param expiresAt      过期时间
 * @param createdAt      创建时间
 * @param updatedAt      最后更新时间
 */
public record IdempotencyRecord(
        String recordId,
        String principalKey,
        String apiScope,
        String idempotencyKey,
        byte[] requestDigest,
        String resourceType,
        String resourceId,
        String responseJson,
        IdempotencyStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isProcessing() {
        return status == IdempotencyStatus.PROCESSING;
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}
