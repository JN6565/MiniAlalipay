package com.minialalipay.ai.domain.agent;

import java.time.Instant;

/**
 * 用户偏好记录。
 *
 * <p>存储用户对 AI 助手的个性化设置，如常用收款人、默认金额等。
 * 每个用户每种偏好类型只保留一条活跃记录（唯一约束）。</p>
 *
 * @param preferenceId  主键（ULID）
 * @param userId        用户 ID
 * @param preferenceType 偏好类型（如 LAST_PAYEE、DEFAULT_AMOUNT）
 * @param value         偏好值（JSON 字符串，存储结构化数据）
 * @param status        状态：ACTIVE=有效，REVOKED=已撤销
 * @param createdAt     创建时间
 * @param updatedAt     更新时间
 */
public record UserPreference(
        String preferenceId,
        String userId,
        String preferenceType,
        String value,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    /** 偏好状态：有效 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 偏好状态：已撤销 */
    public static final String STATUS_REVOKED = "REVOKED";

    /** 偏好类型：最近使用的收款人 */
    public static final String TYPE_LAST_PAYEE = "LAST_PAYEE";
    /** 偏好类型：默认转账金额（分） */
    public static final String TYPE_DEFAULT_AMOUNT = "DEFAULT_AMOUNT";
}
