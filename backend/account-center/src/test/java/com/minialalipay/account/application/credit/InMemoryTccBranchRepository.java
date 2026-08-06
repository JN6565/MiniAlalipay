package com.minialalipay.account.application.credit;

import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 测试专用 TCC 分支仓储，用于验证持久化屏障、幂等和恢复语义。 */
final class InMemoryTccBranchRepository implements TccBranchRepository {
    private final Map<String, TccBranch> accountBranches = new HashMap<>();
    private final Map<String, TccBranch> ledgerBranches = new HashMap<>();

    @Override
    public Optional<TccBranch> findAccountBranchForUpdate(String xid, TccBranchType type, String resourceId) {
        return Optional.ofNullable(accountBranches.get(key(xid, type, resourceId)));
    }

    @Override
    public void createAccountBranch(TccBranch branch) {
        accountBranches.put(key(branch.getXid(), branch.getBranchType(), branch.getResourceId()), branch);
    }

    @Override
    public boolean updateAccountBranch(TccBranch branch, long expectedVersion) {
        String key = key(branch.getXid(), branch.getBranchType(), branch.getResourceId());
        TccBranch current = accountBranches.get(key);
        if (current == null || current.getBarrierVersion() != branch.getBarrierVersion()) {
            return false;
        }
        accountBranches.put(key, branch);
        return branch.getBarrierVersion() == expectedVersion + 1;
    }

    @Override
    public boolean allAccountBranches(String transactionId, TccBranchStatus status, int expectedCount) {
        long count = accountBranches.values().stream()
                .filter(branch -> branch.getTransactionId().equals(transactionId))
                .filter(branch -> branch.getStatus() == status)
                .count();
        return count == expectedCount;
    }

    @Override
    public Optional<TccBranch> findLedgerBranchForUpdate(String xid, String resourceId) {
        return findLedgerBranchForUpdate(xid, TccBranchType.LEDGER, resourceId);
    }

    @Override
    public Optional<TccBranch> findLedgerBranchForUpdate(String xid, TccBranchType type, String resourceId) {
        return Optional.ofNullable(ledgerBranches.get(key(xid, type, resourceId)));
    }

    @Override
    public void createLedgerBranch(TccBranch branch) {
        ledgerBranches.put(key(branch.getXid(), branch.getBranchType(), branch.getResourceId()), branch);
    }

    @Override
    public boolean updateLedgerBranch(TccBranch branch, long expectedVersion) {
        ledgerBranches.put(key(branch.getXid(), branch.getBranchType(), branch.getResourceId()), branch);
        return branch.getBarrierVersion() == expectedVersion + 1;
    }

    @Override
    public boolean ledgerBranchIs(String transactionId, TccBranchStatus status) {
        return ledgerBranchIs(transactionId, TccBranchType.LEDGER, status);
    }

    @Override
    public boolean ledgerBranchIs(String transactionId, TccBranchType type, TccBranchStatus status) {
        return ledgerBranches.values().stream().anyMatch(branch ->
                branch.getTransactionId().equals(transactionId) && branch.getBranchType() == type
                        && branch.getStatus() == status);
    }

    private static String key(String xid, TccBranchType type, String resourceId) {
        return xid + ':' + type + ':' + resourceId;
    }
}
