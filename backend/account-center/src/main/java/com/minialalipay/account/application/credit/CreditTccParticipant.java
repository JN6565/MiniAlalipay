package com.minialalipay.account.application.credit;

import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditErrorCode;
import com.minialalipay.account.domain.credit.CreditFreeze;
import com.minialalipay.account.domain.credit.CreditFreezeRepository;
import com.minialalipay.account.domain.credit.CreditFreezeStatus;
import com.minialalipay.account.domain.credit.CreditPurchase;
import com.minialalipay.account.domain.credit.CreditPurchaseRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
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
 * 信用支付 TCC 分支参与者（CREDIT_PAY）。
 *
 * <p>负责信用消费三阶段资源管理。Seata 客户端依赖引入后，三个方法可分别添加
 * {@code @TwoPhaseBusinessAction} 注解接入全局协调器。</p>
 *
 * <p>关键设计：
 * <ul>
 *   <li>幂等：基于 (transactionId, creditAccountId) 唯一键</li>
 *   <li>空回滚：Try 未执行时 Cancel 快速返回</li>
 *   <li>防悬挂：Try 发现已 RELEASED 记录时拒绝执行</li>
 *   <li>CAS 乐观锁：仓储实现层保证版本安全</li>
 * </ul>
 * </p>
 */
@Service
public class CreditTccParticipant {

    private static final Logger log = LoggerFactory.getLogger(CreditTccParticipant.class);

    private final CreditAccountRepository creditAccountRepository;
    private final CreditFreezeRepository creditFreezeRepository;
    private final CreditPurchaseRepository creditPurchaseRepository;
    private final CreditReceivableRepository creditReceivableRepository;
    private final TccBranchRepository branchRepository;

    public CreditTccParticipant(
            CreditAccountRepository creditAccountRepository,
            CreditFreezeRepository creditFreezeRepository,
            CreditPurchaseRepository creditPurchaseRepository,
            CreditReceivableRepository creditReceivableRepository,
            TccBranchRepository branchRepository
    ) {
        this.creditAccountRepository = creditAccountRepository;
        this.creditFreezeRepository = creditFreezeRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.creditReceivableRepository = creditReceivableRepository;
        this.branchRepository = branchRepository;
    }

    /**
     * Try 阶段：冻结信用额度，创建冻结记录。
     *
     * <p>幂等保证：同一 (transactionId, creditAccountId) 重复调用返回已有记录。
     * 防悬挂：如果记录已被 Cancel 释放，拒绝执行 Try。</p>
     *
     * @param transactionId 统一交易 ID
     * @param creditAccountId 信用账户 ID
     * @param amountFen 冻结金额（分）
     * @param branchXid TCC 分支事务 ID
     * @param now 当前时间
     * @return 冻结记录
     * @throws BusinessException 信用账户不存在、不可用或额度不足
     */
    @Transactional
    public CreditFreeze tryFreeze(
            String transactionId, String creditAccountId,
            long amountFen, String branchXid, Instant now
    ) {
        TccBranch branch = initializeBranch(
                branchXid, transactionId, creditAccountId, amountFen, now);
        validateBranch(branch, transactionId, amountFen);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("Cancel 已建立信用支付屏障，拒绝晚到 Try");
        }

        // 已完成 Try 或 Confirm 时只回读同一冻结事实，不重复占用额度。
        Optional<CreditFreeze> existing = creditFreezeRepository
                .findByTransactionIdAndAccountId(transactionId, creditAccountId);
        if (branch.getStatus() != TccBranchStatus.INIT) {
            CreditFreeze freeze = existing.get();
            validateRepeatedFreeze(freeze, amountFen, branchXid);
            return freeze;
        }

        if (existing.isPresent()) {
            validateRepeatedFreeze(existing.get(), amountFen, branchXid);
            long expectedVersion = branch.getBarrierVersion();
            branch.markTried(now);
            saveBranch(branch, expectedVersion);
            return existing.get();
        }

        CreditAccount account = creditAccountRepository.findById(creditAccountId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));

        if (!account.allowsCreditPay()) {
            throw new BusinessException(CreditErrorCode.CREDIT_NOT_AVAILABLE);
        }

        try {
            account.freeze(amountFen, now);
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().contains("可用信用额度不足")) {
                throw new BusinessException(CreditErrorCode.CREDIT_LIMIT_INSUFFICIENT);
            }
            if (e.getMessage() != null && e.getMessage().contains("当前不可用")) {
                throw new BusinessException(CreditErrorCode.CREDIT_NOT_AVAILABLE);
            }
            throw e;
        }
        creditAccountRepository.save(account);

        CreditFreeze freeze = new CreditFreeze(
                generateId(), transactionId, creditAccountId,
                amountFen, branchXid, now
        );
        creditFreezeRepository.save(freeze);

        long expectedVersion = branch.getBarrierVersion();
        branch.markTried(now);
        saveBranch(branch, expectedVersion);

        log.info("CREDIT_PAY Try 成功: transactionId={}, creditAccountId={}, amountFen={}",
                transactionId, creditAccountId, amountFen);
        return freeze;
    }

    /**
     * Confirm 阶段：冻结转已用，创建消费明细，增加信用应收。
     *
     * <p>幂等保证：已确认的冻结记录重复调用直接返回。
     * 如果冻结记录不存在或已释放，抛出异常。</p>
     *
     * @param transactionId 统一交易 ID
     * @param creditAccountId 信用账户 ID
     * @param amountFen Try 阶段冻结金额（分），用于拒绝同键异参重试
     * @param branchXid TCC 全局事务 XID，必须与 Try 阶段一致
     * @param qrOrderId 扫码订单 ID（用于创建消费明细）
     * @param merchantAccountId 收款方账户 ID（用于创建消费明细）
     * @param now 当前时间
     * @throws BusinessException 冻结记录不存在
     * @throws IllegalStateException 冻结记录已释放
     */
    @Transactional
    public void confirmFreeze(
            String transactionId, String creditAccountId,
            long amountFen, String branchXid,
            String qrOrderId, String merchantAccountId, Instant now
    ) {
        CreditFreeze freeze = creditFreezeRepository
                .findByTransactionIdAndAccountId(transactionId, creditAccountId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));

        validateRepeatedFreeze(freeze, amountFen, branchXid);

        TccBranch branch = requiredBranch(branchXid, creditAccountId);
        validateBranch(branch, transactionId, amountFen);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) {
            return;
        }
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("已取消的信用支付分支不可确认: " + transactionId);
        }

        // 幂等：已确认直接返回
        if (freeze.getStatus() == CreditFreezeStatus.CONFIRMED) {
            log.info("CREDIT_PAY Confirm 幂等返回: transactionId={}", transactionId);
            return;
        }

        // 已释放的冻结不可确认
        if (freeze.getStatus() == CreditFreezeStatus.RELEASED) {
            throw new IllegalStateException("已释放的冻结记录不可确认: " + transactionId);
        }

        CreditAccount account = creditAccountRepository.findById(creditAccountId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));

        // 冻结转已用
        account.confirmFreeze(freeze.getAmountFen(), now);
        creditAccountRepository.save(account);

        // 创建消费明细
        CreditPurchase purchase = new CreditPurchase(
                generateId(), transactionId, creditAccountId,
                qrOrderId, merchantAccountId, freeze.getAmountFen(), now
        );
        creditPurchaseRepository.save(purchase);

        // 增加未出账应收
        CreditReceivable receivable = creditReceivableRepository
                .findByCreditAccountId(creditAccountId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
        receivable.increaseUnbilled(freeze.getAmountFen(), now);
        creditReceivableRepository.save(receivable);

        // 标记冻结为已确认
        freeze.confirm(now);
        creditFreezeRepository.save(freeze);

        long expectedVersion = branch.getBarrierVersion();
        branch.confirm(now);
        saveBranch(branch, expectedVersion);

        log.info("CREDIT_PAY Confirm 成功: transactionId={}, creditAccountId={}, amountFen={}",
                transactionId, creditAccountId, freeze.getAmountFen());
    }

    /**
     * Cancel 阶段：释放冻结额度。
     *
     * <p>幂等保证：已取消的分支重复调用直接返回。
     * 空回滚必须持久化 CANCELLED/EMPTY 屏障，晚到 Try 读取屏障后拒绝占用额度。</p>
     *
     * @param transactionId 统一交易 ID
     * @param creditAccountId 信用账户 ID
     * @param amountFen 分支金额（分），用于校验同键异参重试
     * @param branchXid TCC 全局事务 XID
     * @param now 当前时间
     * @throws BusinessException 信用账户不存在（有冻结记录时）
     * @throws IllegalStateException 冻结记录已确认
     */
    @Transactional
    public void cancelFreeze(
            String transactionId, String creditAccountId,
            long amountFen, String branchXid, Instant now
    ) {
        TccBranch branch = findBranch(branchXid, creditAccountId).orElse(null);
        if (branch == null) {
            branch = emptyRollbackBranch(branchXid, transactionId, creditAccountId, amountFen, now);
            log.info("CREDIT_PAY Cancel 空回滚已落屏障: transactionId={}, creditAccountId={}",
                    transactionId, creditAccountId);
            return;
        }
        validateBranch(branch, transactionId, amountFen);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            return;
        }
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) {
            throw new IllegalStateException("已确认的信用支付分支不可取消: " + transactionId);
        }

        Optional<CreditFreeze> existing = creditFreezeRepository
                .findByTransactionIdAndAccountId(transactionId, creditAccountId);

        if (branch.getStatus() == TccBranchStatus.TRIED) {
            CreditFreeze freeze = existing.orElseThrow(() ->
                    new IllegalStateException("信用支付 Try 屏障存在但冻结事实缺失: " + transactionId));
            if (freeze.getStatus() == CreditFreezeStatus.CONFIRMED) {
                throw new IllegalStateException("已确认的冻结记录不可释放: " + transactionId);
            }
            if (freeze.getStatus() != CreditFreezeStatus.RELEASED) {
                CreditAccount account = creditAccountRepository.findById(creditAccountId)
                        .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
                account.releaseFreeze(freeze.getAmountFen(), now);
                creditAccountRepository.save(account);
                freeze.release(now);
                creditFreezeRepository.save(freeze);
            }
        }

        long expectedVersion = branch.getBarrierVersion();
        branch.cancel(now);
        saveBranch(branch, expectedVersion);

        log.info("CREDIT_PAY Cancel 成功: transactionId={}, creditAccountId={}, amountFen={}",
                transactionId, creditAccountId, amountFen);
    }

    private TccBranch initializeBranch(String xid, String transactionId, String resourceId,
                                       long amountFen, Instant now) {
        Optional<TccBranch> existing = findBranch(xid, resourceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        TccBranch branch = TccBranch.initialize(
                xid, TccBranchType.CREDIT_PAY, resourceId, transactionId, amountFen, now);
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
                xid, TccBranchType.CREDIT_PAY, resourceId, transactionId, amountFen, now);
        try {
            branchRepository.createAccountBranch(branch);
            return branch;
        } catch (DataIntegrityViolationException conflict) {
            return requiredBranch(xid, resourceId);
        }
    }

    private Optional<TccBranch> findBranch(String xid, String resourceId) {
        return branchRepository.findAccountBranchForUpdate(xid, TccBranchType.CREDIT_PAY, resourceId);
    }

    private TccBranch requiredBranch(String xid, String resourceId) {
        return findBranch(xid, resourceId).orElseThrow(() ->
                new IllegalStateException("信用支付 TCC 分支屏障不存在"));
    }

    private void validateBranch(TccBranch branch, String transactionId, long amountFen) {
        if (!branch.getTransactionId().equals(transactionId) || branch.getAmountFen() != amountFen) {
            throw new IllegalStateException("信用支付 TCC 分支幂等参数不一致");
        }
    }

    private void saveBranch(TccBranch branch, long expectedVersion) {
        if (!branchRepository.updateAccountBranch(branch, expectedVersion)) {
            throw new IllegalStateException("信用支付 TCC 分支版本冲突");
        }
    }

    private void validateRepeatedFreeze(CreditFreeze existing, long amountFen, String branchXid) {
        if (existing.getAmountFen() != amountFen || !existing.getBranchXid().equals(branchXid)) {
            throw new BusinessException(CreditErrorCode.CREDIT_NOT_AVAILABLE);
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
