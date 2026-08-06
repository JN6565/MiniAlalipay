package com.minialalipay.business.domain.collection;

import java.time.Instant;
import java.util.Objects;

/**
 * 用户长期个人收款码聚合。
 *
 * <p>唯一有效码约束由仓储的用户唯一索引和版本 CAS 共同保证；本聚合仅表达单个码的状态，
 * 换码应用服务必须在同一事务中停用旧码并创建新码。</p>
 */
public final class PersonalCollectionCode {
    private final String codeId;
    private final String userId;
    private final String accountId;
    private PersonalCollectionCodeStatus status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /** 创建一个可用的个人收款码。 */
    public static PersonalCollectionCode activate(String codeId, String userId, String accountId, Instant now) {
        return new PersonalCollectionCode(codeId, userId, accountId, PersonalCollectionCodeStatus.ACTIVE, 0L, now, now);
    }

    /** 从持久化事实重建个人收款码。 */
    public PersonalCollectionCode(String codeId, String userId, String accountId, PersonalCollectionCodeStatus status,
                                  long version, Instant createdAt, Instant updatedAt) {
        this.codeId = required(codeId, "个人收款码 ID");
        this.userId = required(userId, "用户 ID");
        this.accountId = required(accountId, "收款账户 ID");
        this.status = Objects.requireNonNull(status, "个人收款码状态不能为空");
        if (version < 0) throw new IllegalArgumentException("个人收款码版本不得为负数");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /** 将旧码标记为已替换，供同一事务创建的新码接替。 */
    public void replace(long expectedVersion, Instant now) {
        transition(expectedVersion, PersonalCollectionCodeStatus.REPLACED, now, "个人收款码当前不可替换");
    }

    /** 停用当前个人收款码。 */
    public void deactivate(long expectedVersion, Instant now) {
        transition(expectedVersion, PersonalCollectionCodeStatus.DISABLED, now, "个人收款码当前不可停用");
    }

    /** 校验个人码仍可被外部会话使用。 */
    public void ensureActive() {
        if (status != PersonalCollectionCodeStatus.ACTIVE) {
            throw new IllegalStateException("个人收款码不可用");
        }
    }

    private void transition(long expectedVersion, PersonalCollectionCodeStatus target, Instant now, String message) {
        if (version != expectedVersion) throw new IllegalStateException("个人收款码版本已经变化");
        if (status != PersonalCollectionCodeStatus.ACTIVE) throw new IllegalStateException(message);
        status = target;
        version++;
        updatedAt = now;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getCodeId() { return codeId; }
    public String getUserId() { return userId; }
    public String getAccountId() { return accountId; }
    public PersonalCollectionCodeStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
