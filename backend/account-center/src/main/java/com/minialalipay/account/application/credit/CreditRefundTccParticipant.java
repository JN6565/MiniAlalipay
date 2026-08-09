package com.minialalipay.account.application.credit;

import com.minialalipay.account.domain.credit.CreditErrorCode;
import com.minialalipay.account.domain.credit.CreditPurchase;
import com.minialalipay.account.domain.credit.CreditPurchaseBillingStatus;
import com.minialalipay.account.domain.credit.CreditPurchaseRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.domain.tcc.TccBranch;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import com.minialalipay.common.error.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 信用支付退款冲正 TCC 分支参与者（REFUND）。
 *
 * <p>原信用消费已入账为未出账/已出账应收；退款冲正只针对尚未还款的全额消费：
 * Try 校验并锁定消费明细与应收汇总，Confirm 将消费标记为 {@link CreditPurchaseBillingStatus#REVERSED}
 * 并按退款金额减少应收，Cancel 只落空回滚屏障。收款方余额冻结与退款账本由其余分支负责。</p>
 */
@Service
public class CreditRefundTccParticipant {

    private final CreditPurchaseRepository creditPurchaseRepository;
    private final CreditReceivableRepository creditReceivableRepository;
    private final TccBranchRepository branchRepository;

    public CreditRefundTccParticipant(CreditPurchaseRepository creditPurchaseRepository,
                                      CreditReceivableRepository creditReceivableRepository,
                                      TccBranchRepository branchRepository) {
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.creditReceivableRepository = creditReceivableRepository;
        this.branchRepository = branchRepository;
    }

    /**
     * Try 阶段：锁定原信用消费并校验可退款资格。
     *
     * <p>同一 {@code xid + originalTransactionId} 重试幂等；Cancel 已先到时拒绝晚到 Try。</p>
     *
     * @param refundTransactionId REFUND 统一交易 ID
     * @param originalTransactionId 原 CREDIT_PAY 交易 ID
     * @param merchantAccountId 退款发起人（原收款方）账户 ID
     * @param amountFen 退款金额（分）
     * @param branchXid TCC 全局事务 XID
     * @param now 当前时间
     * @return 退款冲正分支
     */
    @Transactional
    public TccBranch tryRefund(String refundTransactionId, String originalTransactionId, String merchantAccountId,
                               long amountFen, String branchXid, Instant now) {
        TccBranch branch = initializeBranch(branchXid, refundTransactionId, originalTransactionId, amountFen, now);
        validateBranch(branch, refundTransactionId, amountFen);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("Cancel 已建立退款冲正屏障，拒绝晚到 Try");
        }
        if (branch.getStatus() != TccBranchStatus.INIT) {
            return branch;
        }
        CreditPurchase purchase = requirePurchase(originalTransactionId);
        validateRefundable(purchase, merchantAccountId, amountFen);
        creditReceivableRepository.findByCreditAccountId(purchase.getCreditAccountId())
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
        long expectedVersion = branch.getBarrierVersion();
        branch.markTried(now);
        saveBranch(branch, expectedVersion);
        return branch;
    }

    /**
     * Confirm 阶段：标记消费冲正终态并减少信用应收。
     *
     * <p>幂等保证：已确认分支重复调用直接返回；消费与应收在同一本地事务提交，
     * 恢复扫描重试不会重复冲正。</p>
     */
    @Transactional
    public TccBranch confirmRefund(String refundTransactionId, String originalTransactionId, String merchantAccountId,
                                   long amountFen, String branchXid, Instant now) {
        TccBranch branch = requiredBranch(branchXid, originalTransactionId);
        validateBranch(branch, refundTransactionId, amountFen);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) {
            return branch;
        }
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("已取消的退款冲正分支不可确认");
        }
        CreditPurchase purchase = requirePurchase(originalTransactionId);
        validateRefundable(purchase, merchantAccountId, amountFen);
        purchase.applyRefund(refundTransactionId, amountFen, now);
        creditPurchaseRepository.save(purchase);
        CreditReceivable receivable = creditReceivableRepository
                .findByCreditAccountId(purchase.getCreditAccountId())
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
        receivable.decreaseByRefund(amountFen, now);
        creditReceivableRepository.save(receivable);
        long expectedVersion = branch.getBarrierVersion();
        branch.confirm(now);
        saveBranch(branch, expectedVersion);
        return branch;
    }

    /**
     * Cancel 阶段：落空回滚屏障；Try 未执行时也需拒绝晚到 Try。
     *
     * <p>Try 只校验不占资源，Cancel 无资源释放。</p>
     */
    @Transactional
    public TccBranch cancelRefund(String refundTransactionId, String originalTransactionId, String merchantAccountId,
                                  long amountFen, String branchXid, Instant now) {
        TccBranch branch = findBranch(branchXid, originalTransactionId).orElse(null);
        if (branch == null) {
            branch = TccBranch.emptyRollback(branchXid, TccBranchType.REFUND, originalTransactionId,
                    refundTransactionId, amountFen, now);
            try {
                branchRepository.createAccountBranch(branch);
                return branch;
            } catch (DataIntegrityViolationException conflict) {
                return requiredBranch(branchXid, originalTransactionId);
            }
        }
        validateBranch(branch, refundTransactionId, amountFen);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            return branch;
        }
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) {
            throw new IllegalStateException("已确认的退款冲正分支不可取消");
        }
        long expectedVersion = branch.getBarrierVersion();
        branch.cancel(now);
        saveBranch(branch, expectedVersion);
        return branch;
    }

    private CreditPurchase requirePurchase(String originalTransactionId) {
        return creditPurchaseRepository.findByCreditTransactionId(originalTransactionId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));
    }

    private void validateRefundable(CreditPurchase purchase, String merchantAccountId, long amountFen) {
        if (!purchase.getMerchantAccountId().equals(merchantAccountId)) {
            throw new IllegalStateException("退款发起账户与原收款账户不一致");
        }
        if (purchase.getAmountFen() != amountFen) {
            throw new IllegalStateException("退款金额与原信用消费金额不一致");
        }
        if (purchase.getRefundedFen() > 0 || purchase.getBillingStatus() == CreditPurchaseBillingStatus.REVERSED) {
            throw new IllegalStateException("原信用消费已退款");
        }
        if (purchase.getBillingStatus() == CreditPurchaseBillingStatus.REPAID) {
            throw new IllegalStateException("已还清的信用消费不可退款");
        }
    }

    private TccBranch initializeBranch(String xid, String transactionId, String resourceId,
                                       long amountFen, Instant now) {
        Optional<TccBranch> existing = findBranch(xid, resourceId);
        if (existing.isPresent()) {
            return existing.get();
        }
        TccBranch branch = TccBranch.initialize(xid, TccBranchType.REFUND, resourceId, transactionId, amountFen, now);
        try {
            branchRepository.createAccountBranch(branch);
            return branch;
        } catch (DataIntegrityViolationException conflict) {
            return requiredBranch(xid, resourceId);
        }
    }

    private Optional<TccBranch> findBranch(String xid, String resourceId) {
        return branchRepository.findAccountBranchForUpdate(xid, TccBranchType.REFUND, resourceId);
    }

    private TccBranch requiredBranch(String xid, String resourceId) {
        return findBranch(xid, resourceId).orElseThrow(() ->
                new IllegalStateException("退款冲正 TCC 分支屏障不存在"));
    }

    private void validateBranch(TccBranch branch, String transactionId, long amountFen) {
        if (!branch.getTransactionId().equals(transactionId) || branch.getAmountFen() != amountFen) {
            throw new IllegalStateException("退款冲正 TCC 分支幂等参数不一致");
        }
    }

    private void saveBranch(TccBranch branch, long expectedVersion) {
        if (!branchRepository.updateAccountBranch(branch, expectedVersion)) {
            throw new IllegalStateException("退款冲正 TCC 分支版本冲突");
        }
    }
}
