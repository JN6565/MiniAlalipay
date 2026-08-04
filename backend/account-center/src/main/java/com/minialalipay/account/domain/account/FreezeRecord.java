package com.minialalipay.account.domain.account;

import java.time.Instant;
import java.util.Objects;

/**
 * 单笔交易在一个余额账户上的冻结事实。
 *
 * <p>唯一业务键为交易、账户和用途。状态进入 CONFIRMED 或 RELEASED 后即为终态，
 * 重复同向回调保持幂等，反向回调必须拒绝。</p>
 */
public final class FreezeRecord {

    private final String freezeId;
    private final String transactionId;
    private final String accountId;
    private final FreezePurpose purpose;
    private final long amountFen;
    private final String branchXid;
    private FreezeStatus status;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /** 创建已冻结记录。 */
    public static FreezeRecord create(String freezeId, String transactionId, String accountId,
                                      FreezePurpose purpose, long amountFen, String branchXid, Instant now) {
        return new FreezeRecord(freezeId, transactionId, accountId, purpose, amountFen, branchXid,
                FreezeStatus.FROZEN, 0L, now, now);
    }

    /** 从持久化事实重建冻结记录。 */
    public FreezeRecord(String freezeId, String transactionId, String accountId, FreezePurpose purpose,
                        long amountFen, String branchXid, FreezeStatus status, long version,
                        Instant createdAt, Instant updatedAt) {
        this.freezeId = requireText(freezeId, "冻结记录 ID 不能为空");
        this.transactionId = requireText(transactionId, "交易 ID 不能为空");
        this.accountId = requireText(accountId, "账户 ID 不能为空");
        this.purpose = Objects.requireNonNull(purpose, "冻结用途不能为空");
        if (amountFen <= 0) {
            throw new IllegalArgumentException("冻结金额必须为正");
        }
        this.amountFen = amountFen;
        this.branchXid = requireText(branchXid, "TCC XID 不能为空");
        this.status = Objects.requireNonNull(status, "冻结状态不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("冻结版本不得为负");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /** 将活动冻结确认；重复确认不改变事实。 */
    public void confirm(Instant now) {
        if (status == FreezeStatus.CONFIRMED) {
            return;
        }
        if (status == FreezeStatus.RELEASED) {
            throw new IllegalStateException("已释放的冻结记录不能确认");
        }
        status = FreezeStatus.CONFIRMED;
        updatedAt = Objects.requireNonNull(now, "更新时间不能为空");
    }

    /** 将活动冻结释放；重复释放不改变事实。 */
    public void cancel(Instant now) {
        if (status == FreezeStatus.RELEASED) {
            return;
        }
        if (status == FreezeStatus.CONFIRMED) {
            throw new IllegalStateException("已确认的冻结记录不能释放");
        }
        status = FreezeStatus.RELEASED;
        updatedAt = Objects.requireNonNull(now, "更新时间不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    /** 持久化 CAS 成功后更新本地版本。 */
    public void updateVersion(long version) { this.version = version; }
    /** @return 冻结记录 ID */
    public String getFreezeId() { return freezeId; }
    /** @return 交易 ID */
    public String getTransactionId() { return transactionId; }
    /** @return 账户 ID */
    public String getAccountId() { return accountId; }
    /** @return 冻结用途 */
    public FreezePurpose getPurpose() { return purpose; }
    /** @return 冻结金额，单位分 */
    public long getAmountFen() { return amountFen; }
    /** @return TCC 全局事务 XID */
    public String getBranchXid() { return branchXid; }
    /** @return 冻结状态 */
    public FreezeStatus getStatus() { return status; }
    /** @return CAS 版本 */
    public long getVersion() { return version; }
    /** @return 创建时间 */
    public Instant getCreatedAt() { return createdAt; }
    /** @return 更新时间 */
    public Instant getUpdatedAt() { return updatedAt; }
}
