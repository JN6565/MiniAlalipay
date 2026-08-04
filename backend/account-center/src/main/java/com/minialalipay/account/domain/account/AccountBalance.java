package com.minialalipay.account.domain.account;

import java.time.Instant;
import java.util.Objects;

/**
 * 账户余额聚合，维护可用金额、冻结金额和乐观锁版本。
 *
 * <p>所有金额单位均为分。Try 只在可用与冻结之间移动金额，Confirm 才使付款账户总额减少，
 * Cancel 将冻结金额原样恢复，三个动作都必须保护金额非负不变量。</p>
 */
public final class AccountBalance {

    private final String accountId;
    private long availableFen;
    private long frozenFen;
    private long version;
    private Instant updatedAt;

    /**
     * 创建初始为零的余额事实。
     *
     * @param accountId 账户 ID
     * @param now 创建时间
     * @return 零余额
     */
    public static AccountBalance zero(String accountId, Instant now) {
        return new AccountBalance(accountId, 0L, 0L, 0L, now);
    }

    /**
     * 从持久化事实重建余额。
     *
     * @param accountId 账户 ID
     * @param availableFen 可用金额，单位分
     * @param frozenFen 冻结金额，单位分
     * @param version CAS 版本
     * @param updatedAt 最近更新时间
     */
    public AccountBalance(String accountId, long availableFen, long frozenFen, long version, Instant updatedAt) {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("账户 ID 不能为空");
        }
        this.accountId = accountId;
        this.availableFen = availableFen;
        this.frozenFen = frozenFen;
        this.version = version;
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
        validate();
    }

    /** 将可用金额冻结，且保持总余额不变。 */
    public void freeze(long amountFen, Instant now) {
        requirePositive(amountFen, "冻结金额必须为正");
        if (availableFen < amountFen) {
            throw new IllegalStateException("账户可用余额不足");
        }
        availableFen -= amountFen;
        frozenFen += amountFen;
        updatedAt = Objects.requireNonNull(now, "更新时间不能为空");
        validate();
    }

    /** Confirm 扣除已经冻结的金额。 */
    public void confirm(long amountFen, Instant now) {
        requireFrozen(amountFen, "确认金额必须为正");
        frozenFen -= amountFen;
        updatedAt = Objects.requireNonNull(now, "更新时间不能为空");
        validate();
    }

    /** Cancel 将已经冻结的金额恢复为可用金额。 */
    public void cancel(long amountFen, Instant now) {
        requireFrozen(amountFen, "释放金额必须为正");
        frozenFen -= amountFen;
        availableFen += amountFen;
        updatedAt = Objects.requireNonNull(now, "更新时间不能为空");
        validate();
    }

    /** 收款分支 Confirm 后增加可用余额；Try 阶段不提前展示入账。 */
    public void credit(long amountFen, Instant now) {
        requirePositive(amountFen, "入账金额必须为正");
        availableFen = Math.addExact(availableFen, amountFen);
        updatedAt = Objects.requireNonNull(now, "更新时间不能为空");
        validate();
    }

    private void requireFrozen(long amountFen, String message) {
        requirePositive(amountFen, message);
        if (frozenFen < amountFen) {
            throw new IllegalStateException("账户冻结余额不足");
        }
    }

    private static void requirePositive(long amountFen, String message) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validate() {
        if (availableFen < 0 || frozenFen < 0) {
            throw new IllegalStateException("账户余额不得为负");
        }
        if (version < 0) {
            throw new IllegalStateException("余额版本不得为负");
        }
    }

    /** 持久化 CAS 成功后更新本地版本。 */
    public void updateVersion(long version) { this.version = version; }
    /** @return 账户 ID */
    public String getAccountId() { return accountId; }
    /** @return 可用金额，单位分 */
    public long getAvailableFen() { return availableFen; }
    /** @return 冻结金额，单位分 */
    public long getFrozenFen() { return frozenFen; }
    /** @return 总金额，单位分 */
    public long getTotalFen() { return Math.addExact(availableFen, frozenFen); }
    /** @return 余额版本 */
    public long getVersion() { return version; }
    /** @return 最近更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }
}
