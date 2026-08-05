package com.minialalipay.account.application.credit;

import com.minialalipay.account.application.account.BalanceApplicationService;
import com.minialalipay.account.domain.account.FreezePurpose;
import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditErrorCode;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.domain.repayment.CreditRepayment;
import com.minialalipay.account.domain.repayment.CreditRepaymentRepository;
import com.minialalipay.account.domain.repayment.CreditRepaymentStatus;
import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import com.minialalipay.common.error.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 信用还款 TCC 分支参与者（CREDIT_REPAY）。
 *
 * <p>负责信用还款三阶段资源管理，联动余额冻结、信用应收减少和额度恢复。
 * Seata 客户端依赖引入后，三个方法可分别添加
 * {@code @TwoPhaseBusinessAction} 注解接入全局协调器。</p>
 *
 * <p>关键设计：
 * <ul>
 *   <li>幂等：基于 (transactionId, accountId, CREDIT_REPAYMENT) 余额冻结唯一键</li>
 *   <li>金额守恒：sum(allocation.amount) == repayment.amount</li>
 *   <li>分配顺序固化：Try 阶段锁定分配计划，Confirm 不得重新计算</li>
 *   <li>余额操作通过 {@link BalanceApplicationService} 委托，复用幂等和 CAS</li>
 * </ul>
 * </p>
 */
@Service
public class CreditRepayTccParticipant {

    private static final Logger log = LoggerFactory.getLogger(CreditRepayTccParticipant.class);

    private final BalanceApplicationService balanceApplicationService;
    private final CreditAccountRepository creditAccountRepository;
    private final CreditReceivableRepository creditReceivableRepository;
    private final CreditRepaymentRepository creditRepaymentRepository;
    private final TccBranchRepository branchRepository;

    public CreditRepayTccParticipant(
            BalanceApplicationService balanceApplicationService,
            CreditAccountRepository creditAccountRepository,
            CreditReceivableRepository creditReceivableRepository,
            CreditRepaymentRepository creditRepaymentRepository,
            TccBranchRepository branchRepository
    ) {
        this.balanceApplicationService = balanceApplicationService;
        this.creditAccountRepository = creditAccountRepository;
        this.creditReceivableRepository = creditReceivableRepository;
        this.creditRepaymentRepository = creditRepaymentRepository;
        this.branchRepository = branchRepository;
    }

    /**
     * Try 阶段：冻结用户余额，预占还款资金。
     *
     * <p>幂等保证：同一 (transactionId, accountId, CREDIT_REPAYMENT) 重复调用返回已有冻结。
     * 余额操作委托给 {@link BalanceApplicationService#freeze}，复用其幂等和 CAS 逻辑。</p>
     *
     * @param transactionId 统一交易 ID
     * @param accountId 付款方余额账户 ID
     * @param creditAccountId 信用账户 ID
     * @param amountFen 还款金额（分）
     * @param branchXid TCC 分支事务 ID
     * @param now 当前时间
     * @return 本次调用完成后的信用还款分支状态
     * @throws BusinessException 余额不足、账户不可用或版本冲突
     */
    @Transactional
    public TccBranchStatus tryRepay(
            String transactionId, String accountId, String creditAccountId,
            long amountFen, String branchXid, Instant now
    ) {
        TccBranch branch = initializeBranch(
                branchXid, transactionId, creditAccountId, amountFen, now);
        validateBranch(branch, transactionId, amountFen);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("Cancel 已建立信用还款屏障，拒绝晚到 Try");
        }
        if (branch.getStatus() != TccBranchStatus.INIT) {
            return branch.getStatus();
        }

        // 余额冻结委托给 BalanceApplicationService，它已有幂等 + CAS + 唯一键屏障
        balanceApplicationService.freeze(
                generateId(), transactionId, accountId,
                FreezePurpose.CREDIT_REPAYMENT, amountFen, branchXid, now
        );

        long expectedVersion = branch.getBarrierVersion();
        branch.markTried(now);
        saveBranch(branch, expectedVersion);

        log.info("CREDIT_REPAY Try 成功: transactionId={}, accountId={}, amountFen={}",
                transactionId, accountId, amountFen);
        return TccBranchStatus.TRIED;
    }

    /**
     * Confirm 阶段：确认余额扣减，减少信用应收，恢复可用额度，标记还款成功。
     *
     * <p>幂等保证：已成功的还款重复调用直接返回。
     * 金额守恒：Confirm 扣减的余额 == Try 冻结的金额 == 还款金额。</p>
     *
     * @param transactionId 统一交易 ID
     * @param accountId 付款方余额账户 ID
     * @param creditAccountId 信用账户 ID
     * @param amountFen 还款金额（分）
     * @param branchXid TCC 全局事务 XID
     * @param now 当前时间
     * @throws BusinessException 冻结记录不存在或信用账户不存在
     */
    @Transactional
    public void confirmRepay(
            String transactionId, String accountId, String creditAccountId,
            long amountFen, String branchXid, Instant now
    ) {
        TccBranch branch = requiredBranch(branchXid, creditAccountId);
        validateBranch(branch, transactionId, amountFen);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) {
            return;
        }
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("已取消的信用还款分支不可确认: " + transactionId);
        }

        // 检查还款记录是否已成功（幂等）
        CreditRepayment repayment = creditRepaymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.REPAYMENT_NOT_FOUND));
        validateRepayment(repayment, creditAccountId, amountFen);
        if (repayment.getStatus() == CreditRepaymentStatus.SUCCESS) {
            log.info("CREDIT_REPAY Confirm 幂等返回: transactionId={}", transactionId);
            return;
        }

        // 确认余额扣减（委托 BalanceApplicationService，复用幂等）
        balanceApplicationService.confirm(
                transactionId, accountId, FreezePurpose.CREDIT_REPAYMENT, now
        );

        // 减少信用应收
        CreditReceivable receivable = creditReceivableRepository
                .findByCreditAccountId(creditAccountId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
        receivable.decreaseByRepayment(amountFen, now);
        creditReceivableRepository.save(receivable);

        // 恢复信用账户可用额度（减少已用额度）
        CreditAccount account = creditAccountRepository.findById(creditAccountId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
        account.restoreByRepayment(amountFen, now);
        creditAccountRepository.save(account);

        // 标记还款成功
        repayment.markSuccess(now);
        creditRepaymentRepository.save(repayment);

        long expectedVersion = branch.getBarrierVersion();
        branch.confirm(now);
        saveBranch(branch, expectedVersion);

        log.info("CREDIT_REPAY Confirm 成功: transactionId={}, creditAccountId={}, amountFen={}",
                transactionId, creditAccountId, amountFen);
    }

    /**
     * Cancel 阶段：释放余额冻结，标记还款取消。
     *
     * <p>幂等保证：已取消的分支重复调用直接返回。
     * 空回滚必须持久化 CANCELLED/EMPTY 屏障，晚到 Try 读取屏障后拒绝冻结余额。</p>
     *
     * @param transactionId 统一交易 ID
     * @param accountId 付款方余额账户 ID
     * @param creditAccountId 信用账户 ID
     * @param amountFen 分支金额（分），用于识别同键异参重试
     * @param branchXid TCC 全局事务 XID
     * @param now 当前时间
     */
    @Transactional
    public void cancelRepay(
            String transactionId, String accountId, String creditAccountId,
            long amountFen, String branchXid, Instant now
    ) {
        TccBranch branch = findBranch(branchXid, creditAccountId).orElse(null);
        if (branch == null) {
            branch = emptyRollbackBranch(
                    branchXid, transactionId, creditAccountId, amountFen, now);
            cancelRepaymentFact(transactionId, creditAccountId, amountFen, now);
            log.info("CREDIT_REPAY Cancel 空回滚已落屏障: transactionId={}", transactionId);
            return;
        }
        validateBranch(branch, transactionId, amountFen);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            return;
        }
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) {
            throw new IllegalStateException("已确认的信用还款分支不可取消: " + transactionId);
        }

        if (branch.getStatus() == TccBranchStatus.TRIED) {
            balanceApplicationService.cancel(
                    transactionId, accountId, FreezePurpose.CREDIT_REPAYMENT, now
            );
        }

        cancelRepaymentFact(transactionId, creditAccountId, amountFen, now);

        long expectedVersion = branch.getBarrierVersion();
        branch.cancel(now);
        saveBranch(branch, expectedVersion);

        log.info("CREDIT_REPAY Cancel 成功: transactionId={}, creditAccountId={}",
                transactionId, creditAccountId);
    }

    private TccBranch initializeBranch(String xid, String transactionId, String resourceId,
                                       long amountFen, Instant now) {
        Optional<TccBranch> existing = findBranch(xid, resourceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        TccBranch branch = TccBranch.initialize(
                xid, TccBranchType.CREDIT_REPAY, resourceId, transactionId, amountFen, now);
        try {
            branchRepository.createAccountBranch(branch);
            return branch;
        } catch (DataIntegrityViolationException conflict) {
            return requiredBranch(xid, resourceId);
        }
    }

    private TccBranch emptyRollbackBranch(String xid, String transactionId, String resourceId,
                                          long amountFen, Instant now) {
        TccBranch branch = TccBranch.emptyRollback(
                xid, TccBranchType.CREDIT_REPAY, resourceId, transactionId, amountFen, now);
        try {
            branchRepository.createAccountBranch(branch);
            return branch;
        } catch (DataIntegrityViolationException conflict) {
            return requiredBranch(xid, resourceId);
        }
    }

    private Optional<TccBranch> findBranch(String xid, String resourceId) {
        return branchRepository.findAccountBranchForUpdate(xid, TccBranchType.CREDIT_REPAY, resourceId);
    }

    private TccBranch requiredBranch(String xid, String resourceId) {
        return findBranch(xid, resourceId).orElseThrow(() ->
                new IllegalStateException("信用还款 TCC 分支屏障不存在"));
    }

    private void validateBranch(TccBranch branch, String transactionId, long amountFen) {
        if (!branch.getTransactionId().equals(transactionId) || branch.getAmountFen() != amountFen) {
            throw new IllegalStateException("信用还款 TCC 分支幂等参数不一致");
        }
    }

    private void validateRepayment(CreditRepayment repayment, String creditAccountId, long amountFen) {
        if (!repayment.getCreditAccountId().equals(creditAccountId)
                || repayment.getAmountFen() != amountFen) {
            throw new IllegalStateException("信用还款事实与 TCC 分支参数不一致");
        }
    }

    private void cancelRepaymentFact(String transactionId, String creditAccountId,
                                     long amountFen, Instant now) {
        Optional<CreditRepayment> existing = creditRepaymentRepository.findByTransactionId(transactionId);
        if (existing.isEmpty()) {
            return;
        }
        CreditRepayment repayment = existing.get();
        validateRepayment(repayment, creditAccountId, amountFen);
        if (repayment.getStatus() == CreditRepaymentStatus.CANCELLED) {
            return;
        }
        repayment.markCancelled(now);
        creditRepaymentRepository.save(repayment);
    }

    private void saveBranch(TccBranch branch, long expectedVersion) {
        if (!branchRepository.updateAccountBranch(branch, expectedVersion)) {
            throw new IllegalStateException("信用还款 TCC 分支版本冲突");
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
