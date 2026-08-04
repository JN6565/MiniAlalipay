package com.minialalipay.account.application.tcc;

import com.minialalipay.account.application.account.BalanceApplicationService;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.AccountErrorCode;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.account.FreezePurpose;
import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 普通转账账户 TCC 参与者。
 *
 * <p>每个动作在 account_db 本地事务内同时推进屏障和余额事实。Cancel 先到必须留下
 * EMPTY 屏障；晚到 Try 不能再冻结或入账。</p>
 */
@Service
public class BalanceTccApplicationService {
    private final TccBranchRepository branchRepository;
    private final BalanceApplicationService balanceService;
    private final AccountRepository accountRepository;

    public BalanceTccApplicationService(TccBranchRepository branchRepository,
                                        BalanceApplicationService balanceService,
                                        AccountRepository accountRepository) {
        this.branchRepository = branchRepository;
        this.balanceService = balanceService;
        this.accountRepository = accountRepository;
    }

    /** 付款 Try：建立屏障并冻结可用余额。 */
    @Transactional
    public TccBranch tryPayer(TccCommand command, Instant now) {
        TccBranch branch = initialize(command, TccBranchType.PAYER_BALANCE, now);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) throw new IllegalStateException("Cancel 已建立屏障，拒绝晚到 Try");
        if (branch.getStatus() != TccBranchStatus.INIT) return sameRequest(branch, command);
        balanceService.freeze(command.freezeId(), command.transactionId(), command.accountId(),
                FreezePurpose.TRANSFER_OUT, command.amountFen(), command.xid(), now);
        long version = branch.getBarrierVersion();
        branch.markTried(now);
        save(branch, version);
        return branch;
    }

    /** 付款 Confirm：只对已冻结分支扣减一次。 */
    @Transactional
    public TccBranch confirmPayer(TccCommand command, Instant now) {
        TccBranch branch = required(command, TccBranchType.PAYER_BALANCE);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) return branch;
        balanceService.confirm(command.transactionId(), command.accountId(), FreezePurpose.TRANSFER_OUT, now);
        long version = branch.getBarrierVersion(); branch.confirm(now); save(branch, version); return branch;
    }

    /** 付款 Cancel：释放冻结；Try 未到时仅记录空回滚。 */
    @Transactional
    public TccBranch cancelPayer(TccCommand command, Instant now) {
        TccBranch branch = find(command, TccBranchType.PAYER_BALANCE);
        if (branch == null) return emptyRollback(command, TccBranchType.PAYER_BALANCE, now);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) return branch;
        balanceService.cancel(command.transactionId(), command.accountId(), FreezePurpose.TRANSFER_OUT, now);
        long version = branch.getBarrierVersion(); branch.cancel(now); save(branch, version); return branch;
    }

    /** 收款 Try：只持久化预占屏障，不提前增加可用余额。 */
    @Transactional
    public TccBranch tryPayee(TccCommand command, Instant now) {
        TccBranch branch = initialize(command, TccBranchType.PAYEE_BALANCE, now);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) throw new IllegalStateException("Cancel 已建立屏障，拒绝晚到 Try");
        if (branch.getStatus() != TccBranchStatus.INIT) return sameRequest(branch, command);
        requiredBalance(command.accountId());
        long version = branch.getBarrierVersion(); branch.markTried(now); save(branch, version); return branch;
    }

    /** 收款 Confirm：在屏障锁内只增加一次收款账户可用余额。 */
    @Transactional
    public TccBranch confirmPayee(TccCommand command, Instant now) {
        TccBranch branch = required(command, TccBranchType.PAYEE_BALANCE);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) return branch;
        AccountBalance balance = requiredBalance(command.accountId());
        long balanceVersion = balance.getVersion(); balance.credit(command.amountFen(), now);
        if (!accountRepository.updateBalanceForActiveAccount(balance, balanceVersion)) {
            throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        }
        long version = branch.getBarrierVersion(); branch.confirm(now); save(branch, version); return branch;
    }

    /** 收款 Cancel：取消预占；Try 未到时记录空回滚。 */
    @Transactional
    public TccBranch cancelPayee(TccCommand command, Instant now) {
        TccBranch branch = find(command, TccBranchType.PAYEE_BALANCE);
        if (branch == null) return emptyRollback(command, TccBranchType.PAYEE_BALANCE, now);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) return branch;
        long version = branch.getBarrierVersion(); branch.cancel(now); save(branch, version); return branch;
    }

    private TccBranch initialize(TccCommand c, TccBranchType type, Instant now) {
        TccBranch existing = find(c, type);
        if (existing != null) return sameRequest(existing, c);
        TccBranch created = TccBranch.initialize(c.xid(), type, c.accountId(), c.transactionId(), c.amountFen(), now);
        try { branchRepository.createAccountBranch(created); return created; }
        catch (DataIntegrityViolationException conflict) { return required(c, type); }
    }
    private TccBranch emptyRollback(TccCommand c, TccBranchType type, Instant now) {
        TccBranch branch = TccBranch.emptyRollback(c.xid(), type, c.accountId(), c.transactionId(), c.amountFen(), now);
        try { branchRepository.createAccountBranch(branch); return branch; }
        catch (DataIntegrityViolationException conflict) { return required(c, type); }
    }
    private TccBranch required(TccCommand c, TccBranchType type) {
        TccBranch value = find(c, type);
        if (value == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return value;
    }
    private TccBranch find(TccCommand c, TccBranchType type) {
        return branchRepository.findAccountBranchForUpdate(c.xid(), type, c.accountId()).orElse(null);
    }
    private TccBranch sameRequest(TccBranch branch, TccCommand c) {
        if (!branch.getTransactionId().equals(c.transactionId()) || branch.getAmountFen() != c.amountFen()) {
            throw new BusinessException(AccountErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return branch;
    }
    private AccountBalance requiredBalance(String accountId) {
        return accountRepository.findBalance(accountId).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }
    private void save(TccBranch branch, long version) {
        if (!branchRepository.updateAccountBranch(branch, version)) throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
    }

    /** TCC 参与者命令；所有金额单位为分。 */
    public record TccCommand(String xid, String transactionId, String accountId, long amountFen, String freezeId) { }
}
