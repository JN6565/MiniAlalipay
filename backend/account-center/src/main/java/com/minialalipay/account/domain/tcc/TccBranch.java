package com.minialalipay.account.domain.tcc;

import java.time.Instant;
import java.util.Objects;

/**
 * TCC 分支屏障，保护分支幂等、空回滚与防悬挂不变量。
 */
public final class TccBranch {
    private final String xid;
    private final TccBranchType branchType;
    private final String resourceId;
    private final String transactionId;
    private final long amountFen;
    private TccBranchStatus status;
    private RollbackType rollbackType;
    private long barrierVersion;
    private final Instant createdAt;
    private Instant updatedAt;

    /** 创建尚未执行 Try 的持久化屏障。 */
    public static TccBranch initialize(String xid, TccBranchType branchType, String resourceId,
                                       String transactionId, long amountFen, Instant now) {
        return new TccBranch(xid, branchType, resourceId, transactionId, amountFen,
                TccBranchStatus.INIT, null, 0L, now, now);
    }

    /** Cancel 先到时创建取消终态，晚到 Try 必须被拒绝。 */
    public static TccBranch emptyRollback(String xid, TccBranchType branchType, String resourceId,
                                          String transactionId, long amountFen, Instant now) {
        return new TccBranch(xid, branchType, resourceId, transactionId, amountFen,
                TccBranchStatus.CANCELLED, RollbackType.EMPTY, 1L, now, now);
    }

    /** 从数据库事实重建分支。 */
    public TccBranch(String xid, TccBranchType branchType, String resourceId, String transactionId,
                     long amountFen, TccBranchStatus status, RollbackType rollbackType,
                     long barrierVersion, Instant createdAt, Instant updatedAt) {
        this.xid = required(xid); this.branchType = Objects.requireNonNull(branchType);
        this.resourceId = required(resourceId); this.transactionId = required(transactionId);
        if (amountFen <= 0) throw new IllegalArgumentException("分支金额必须为正");
        this.amountFen = amountFen; this.status = Objects.requireNonNull(status);
        this.rollbackType = rollbackType;
        if (barrierVersion < 0) throw new IllegalArgumentException("屏障版本不得为负");
        this.barrierVersion = barrierVersion; this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    /** Try 成功后记录资源已预留；取消屏障存在时拒绝悬挂请求。 */
    public void markTried(Instant now) {
        if (status == TccBranchStatus.TRIED) return;
        if (status == TccBranchStatus.CANCELLED) throw new IllegalStateException("Cancel 已建立屏障，拒绝晚到 Try");
        if (status != TccBranchStatus.INIT) throw new IllegalStateException("分支状态不允许 Try");
        status = TccBranchStatus.TRIED; barrierVersion++; updatedAt = now;
    }

    /** 将已 Try 分支推进到确认终态，重复确认幂等。 */
    public void confirm(Instant now) {
        if (status == TccBranchStatus.CONFIRMED) return;
        if (status != TccBranchStatus.TRIED) throw new IllegalStateException("分支未完成 Try，不能 Confirm");
        status = TccBranchStatus.CONFIRMED; barrierVersion++; updatedAt = now;
    }

    /** 将已 Try 分支推进到正常取消终态，重复取消幂等。 */
    public void cancel(Instant now) {
        if (status == TccBranchStatus.CANCELLED) return;
        if (status != TccBranchStatus.TRIED && status != TccBranchStatus.INIT) {
            throw new IllegalStateException("分支状态不允许 Cancel");
        }
        rollbackType = status == TccBranchStatus.INIT ? RollbackType.EMPTY : RollbackType.NORMAL;
        status = TccBranchStatus.CANCELLED;
        barrierVersion++; updatedAt = now;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TCC 分支字段不能为空");
        return value;
    }
    public String getXid() { return xid; }
    public TccBranchType getBranchType() { return branchType; }
    public String getResourceId() { return resourceId; }
    public String getTransactionId() { return transactionId; }
    public long getAmountFen() { return amountFen; }
    public TccBranchStatus getStatus() { return status; }
    public RollbackType getRollbackType() { return rollbackType; }
    public long getBarrierVersion() { return barrierVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
