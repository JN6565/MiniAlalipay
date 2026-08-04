package com.minialalipay.account.domain.account;

import java.time.Instant;
import java.util.Objects;

/**
 * 普通用户虚拟余额账户聚合根，只保存账户身份与生命周期，不直接保存余额。
 *
 * <p>阶段三只允许创建 {@link AccountType#PERSONAL}、人民币账户。账户状态与余额分别使用
 * 独立版本控制，避免状态更新和高频余额更新互相制造无关冲突。</p>
 */
public final class Account {

    private final String accountId;
    private final String userId;
    private final String registrationId;
    private final AccountType accountType;
    private final String currency;
    private final AccountStatus status;
    private final long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * 创建初始正常的个人人民币账户。
     *
     * @param accountId 账户 ULID
     * @param userId 用户 ULID
     * @param registrationId 用户中心生成的注册幂等编号
     * @param now 开户时间
     * @return 新账户
     */
    public static Account open(String accountId, String userId, String registrationId, Instant now) {
        return new Account(accountId, userId, registrationId, AccountType.PERSONAL, "CNY",
                AccountStatus.ACTIVE, 0L, now, now);
    }

    /**
     * 从持久化事实重建账户。
     */
    public Account(String accountId, String userId, String registrationId, AccountType accountType,
                   String currency, AccountStatus status, long version, Instant createdAt, Instant updatedAt) {
        this.accountId = requireText(accountId, "账户 ID 不能为空");
        this.userId = requireText(userId, "用户 ID 不能为空");
        this.registrationId = requireText(registrationId, "注册编号不能为空");
        this.accountType = Objects.requireNonNull(accountType, "账户类型不能为空");
        this.currency = requireText(currency, "币种不能为空");
        this.status = Objects.requireNonNull(status, "账户状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("账户版本不得为负");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** @return 账户 ID */
    public String getAccountId() { return accountId; }
    /** @return 用户 ID */
    public String getUserId() { return userId; }
    /** @return 注册幂等编号 */
    public String getRegistrationId() { return registrationId; }
    /** @return 账户类型 */
    public AccountType getAccountType() { return accountType; }
    /** @return ISO 4217 币种代码，当前固定 CNY */
    public String getCurrency() { return currency; }
    /** @return 账户生命周期状态 */
    public AccountStatus getStatus() { return status; }
    /** @return 账户状态版本 */
    public long getVersion() { return version; }
    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }
    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }
}
