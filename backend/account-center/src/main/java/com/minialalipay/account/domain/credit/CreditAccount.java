package com.minialalipay.account.domain.credit;

import java.time.Instant;
import java.util.Objects;

/**
 * Mini 花呗额度账户聚合根。
 *
 * <p>每用户唯一，固定虚拟授信额度 5000 元（500000 分）。额度不是余额，
 * 不计入用户可用余额。该聚合根只管理额度数字，不触碰 account_balance 表。</p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>{@code totalLimitFen = 500000}（固定值）</li>
 *   <li>{@code available = total - used - frozen}，四项均不得为负</li>
 *   <li>{@code used + frozen <= total}</li>
 * </ul>
 * </p>
 *
 * <p>状态流转：
 * <ul>
 *   <li>{@link CreditAccountStatus#ACTIVE}：正常，可发起信用支付</li>
 *   <li>{@link CreditAccountStatus#SUSPENDED}：逾期暂停，禁止新的 CREDIT_PAY，允许 CREDIT_REPAY</li>
 *   <li>{@link CreditAccountStatus#CLOSED}：已关闭，终态</li>
 * </ul>
 * </p>
 */
public class CreditAccount {

    /** 固定总额度：5000 元 = 500000 分。 */
    public static final long FIXED_TOTAL_LIMIT_FEN = 500_000L;

    private final String creditAccountId;
    private final String userId;
    private final long totalLimitFen;
    private long usedFen;
    private long frozenFen;
    private CreditAccountStatus status;
    private String suspendReason;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /**
     * 开户时创建额度账户，总额度固定 500000 分，状态为 ACTIVE。
     *
     * @param creditAccountId 信用账户 ID（ULID）
     * @param userId 用户 ID（ULID）
     * @param now 创建时间
     */
    public CreditAccount(String creditAccountId, String userId, Instant now) {
        this.creditAccountId = Objects.requireNonNull(creditAccountId, "信用账户 ID 不能为空");
        this.userId = Objects.requireNonNull(userId, "用户 ID 不能为空");
        this.totalLimitFen = FIXED_TOTAL_LIMIT_FEN;
        this.usedFen = 0L;
        this.frozenFen = 0L;
        this.status = CreditAccountStatus.ACTIVE;
        this.suspendReason = null;
        this.version = 0L;
        this.createdAt = Objects.requireNonNull(now, "创建时间不能为空");
        this.updatedAt = now;
    }

    /**
     * 从持久化重建额度账户。
     *
     * @param creditAccountId 信用账户 ID
     * @param userId 用户 ID
     * @param totalLimitFen 总额度（分）
     * @param usedFen 已用额度（分）
     * @param frozenFen 冻结额度（分）
     * @param status 账户状态
     * @param suspendReason 暂停原因
     * @param version 版本号
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public CreditAccount(
            String creditAccountId, String userId, long totalLimitFen,
            long usedFen, long frozenFen, CreditAccountStatus status,
            String suspendReason, long version,
            Instant createdAt, Instant updatedAt
    ) {
        this.creditAccountId = creditAccountId;
        this.userId = userId;
        this.totalLimitFen = totalLimitFen;
        this.usedFen = usedFen;
        this.frozenFen = frozenFen;
        this.status = status;
        this.suspendReason = suspendReason;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        validateInvariants();
    }

    /**
     * Try 阶段：冻结额度。校验可用额度充足且账户状态正常。
     *
     * @param amountFen 冻结金额（分），必须为正
     * @param now 当前时间
     */
    public void freeze(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("冻结金额必须为正");
        }
        if (status != CreditAccountStatus.ACTIVE) {
            throw new IllegalStateException("信用账户当前不可用");
        }
        if (getAvailableFen() < amountFen) {
            throw new IllegalStateException("可用信用额度不足");
        }
        this.frozenFen += amountFen;
        this.updatedAt = now;
        validateInvariants();
    }

    /**
     * Confirm 阶段：冻结转已用。冻结额度减少，已用额度增加。
     *
     * @param amountFen 确认金额（分），必须为正且不超过冻结额度
     * @param now 当前时间
     */
    public void confirmFreeze(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("确认金额必须为正");
        }
        if (this.frozenFen < amountFen) {
            throw new IllegalStateException("冻结额度不足以确认");
        }
        this.frozenFen -= amountFen;
        this.usedFen += amountFen;
        this.updatedAt = now;
        validateInvariants();
    }

    /**
     * Cancel 阶段：释放冻结额度。冻结额度减少，可用额度恢复。
     *
     * @param amountFen 释放金额（分），必须为正且不超过冻结额度
     * @param now 当前时间
     */
    public void releaseFreeze(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("释放金额必须为正");
        }
        if (this.frozenFen < amountFen) {
            throw new IllegalStateException("冻结额度不足以释放");
        }
        this.frozenFen -= amountFen;
        this.updatedAt = now;
        validateInvariants();
    }

    /**
     * 还款后恢复可用额度：减少已用额度。
     *
     * @param amountFen 还款金额（分），必须为正且不超过已用额度
     * @param now 当前时间
     */
    public void restoreByRepayment(long amountFen, Instant now) {
        if (amountFen <= 0) {
            throw new IllegalArgumentException("还款恢复金额必须为正");
        }
        if (this.usedFen < amountFen) {
            throw new IllegalStateException("已用额度不足以恢复");
        }
        this.usedFen -= amountFen;
        this.updatedAt = now;
        validateInvariants();
    }

    /**
     * 暂停信用账户，禁止新的信用支付。SUSPENDED 状态允许余额支付、查询和还款。
     *
     * @param reason 暂停原因
     * @param now 当前时间
     */
    public void suspend(String reason, Instant now) {
        if (this.status == CreditAccountStatus.CLOSED) {
            throw new IllegalStateException("已关闭的信用账户不可暂停");
        }
        this.status = CreditAccountStatus.SUSPENDED;
        this.suspendReason = reason;
        this.updatedAt = now;
    }

    /**
     * 恢复信用账户为正常状态。仅在无逾期账单且无人工冻结时调用。
     *
     * @param now 当前时间
     */
    public void activate(Instant now) {
        if (this.status == CreditAccountStatus.CLOSED) {
            throw new IllegalStateException("已关闭的信用账户不可恢复");
        }
        this.status = CreditAccountStatus.ACTIVE;
        this.suspendReason = null;
        this.updatedAt = now;
    }

    /**
     * 关闭信用账户。终态，不可恢复。
     *
     * @param now 当前时间
     */
    public void close(Instant now) {
        if (this.usedFen > 0 || this.frozenFen > 0) {
            throw new IllegalStateException("存在未结清额度，不可关闭");
        }
        this.status = CreditAccountStatus.CLOSED;
        this.updatedAt = now;
    }

    /**
     * 判断当前状态是否允许发起新的信用支付。
     *
     * @return 仅 ACTIVE 状态返回 true
     */
    public boolean allowsCreditPay() {
        return this.status == CreditAccountStatus.ACTIVE;
    }

    /** @return 可用额度（分）= 总额度 - 已用 - 冻结 */
    public long getAvailableFen() {
        return totalLimitFen - usedFen - frozenFen;
    }

    /** @return 信用账户 ID */
    public String getCreditAccountId() { return creditAccountId; }

    /** @return 用户 ID */
    public String getUserId() { return userId; }

    /** @return 总额度（分） */
    public long getTotalLimitFen() { return totalLimitFen; }

    /** @return 已用额度（分） */
    public long getUsedFen() { return usedFen; }

    /** @return 冻结额度（分） */
    public long getFrozenFen() { return frozenFen; }

    /** @return 账户状态 */
    public CreditAccountStatus getStatus() { return status; }

    /** @return 暂停原因，无暂停时为 null */
    public String getSuspendReason() { return suspendReason; }

    /** @return 乐观锁版本号 */
    public long getVersion() { return version; }

    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }

    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * 更新版本号，用于持久化层 CAS 更新成功后回写。
     *
     * @param version 新版本号
     */
    public void updateVersion(long version) {
        this.version = version;
    }

    /**
     * 校验额度不变量。任何额度变更后必须调用。
     *
     * @throws IllegalStateException 当不变量被违反时
     */
    private void validateInvariants() {
        if (totalLimitFen != FIXED_TOTAL_LIMIT_FEN) {
            throw new IllegalStateException("总额度必须固定为 " + FIXED_TOTAL_LIMIT_FEN + " 分");
        }
        if (usedFen < 0 || frozenFen < 0) {
            throw new IllegalStateException("已用和冻结额度不得为负");
        }
        if (usedFen + frozenFen > totalLimitFen) {
            throw new IllegalStateException("已用与冻结额度之和不得超过总额度");
        }
    }
}
