package com.minialalipay.account.domain.tcc;

import java.util.Optional;

/** TCC 分支屏障仓储，所有状态推进都必须在锁定后使用版本 CAS。 */
public interface TccBranchRepository {
    /** 查询并锁定账户分支。 */
    Optional<TccBranch> findAccountBranchForUpdate(String xid, TccBranchType branchType, String resourceId);
    /** 创建账户分支屏障。 */
    void createAccountBranch(TccBranch branch);
    /** 按屏障版本更新账户分支。 */
    boolean updateAccountBranch(TccBranch branch, long expectedVersion);
    /** 查询指定交易的全部账户分支是否为目标终态。 */
    boolean allAccountBranches(String transactionId, TccBranchStatus status, int expectedCount);
    /**
     * 判断交易是否存在指定类型的账户分支（不限分支状态）。
     *
     * <p>用于终态事实核验识别资金路径：存在 {@link TccBranchType#CREDIT_PAY} 分支即表示
     * 该交易走花呗信用支付，须按信用规则集核验，不能套用余额转账规则。</p>
     */
    default boolean hasAccountBranch(String transactionId, TccBranchType branchType) { return false; }
    /** 查询并锁定账本分支。 */
    Optional<TccBranch> findLedgerBranchForUpdate(String xid, String resourceId);
    /** 查询并锁定指定类型的账本分支，信用支付不得复用普通账本分支。 */
    Optional<TccBranch> findLedgerBranchForUpdate(String xid, TccBranchType branchType, String resourceId);
    /** 创建账本分支。 */
    void createLedgerBranch(TccBranch branch);
    /** 按屏障版本更新账本分支。 */
    boolean updateLedgerBranch(TccBranch branch, long expectedVersion);
    /** 判断交易账本分支是否为目标终态。 */
    boolean ledgerBranchIs(String transactionId, TccBranchStatus status);
    /** 判断指定类型账本分支是否为目标终态。 */
    boolean ledgerBranchIs(String transactionId, TccBranchType branchType, TccBranchStatus status);
}
