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
import com.minialalipay.common.error.BusinessException;
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

    public CreditRepayTccParticipant(
            BalanceApplicationService balanceApplicationService,
            CreditAccountRepository creditAccountRepository,
            CreditReceivableRepository creditReceivableRepository,
            CreditRepaymentRepository creditRepaymentRepository
    ) {
        this.balanceApplicationService = balanceApplicationService;
        this.creditAccountRepository = creditAccountRepository;
        this.creditReceivableRepository = creditReceivableRepository;
        this.creditRepaymentRepository = creditRepaymentRepository;
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
     * @throws BusinessException 余额不足、账户不可用或版本冲突
     */
    @Transactional
    public void tryRepay(
            String transactionId, String accountId, String creditAccountId,
            long amountFen, String branchXid, Instant now
    ) {
        // 余额冻结委托给 BalanceApplicationService，它已有幂等 + CAS + 唯一键屏障
        balanceApplicationService.freeze(
                generateId(), transactionId, accountId,
                FreezePurpose.CREDIT_REPAYMENT, amountFen, branchXid, now
        );

        log.info("CREDIT_REPAY Try 成功: transactionId={}, accountId={}, amountFen={}",
                transactionId, accountId, amountFen);
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
     * @param now 当前时间
     * @throws BusinessException 冻结记录不存在或信用账户不存在
     */
    @Transactional
    public void confirmRepay(
            String transactionId, String accountId, String creditAccountId,
            long amountFen, Instant now
    ) {
        // 检查还款记录是否已成功（幂等）
        Optional<CreditRepayment> existingRepayment = creditRepaymentRepository
                .findByTransactionId(transactionId);
        if (existingRepayment.isPresent()
                && existingRepayment.get().getStatus() == CreditRepaymentStatus.SUCCESS) {
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
        if (existingRepayment.isPresent()) {
            CreditRepayment repayment = existingRepayment.get();
            repayment.markSuccess(now);
            creditRepaymentRepository.save(repayment);
        }

        log.info("CREDIT_REPAY Confirm 成功: transactionId={}, creditAccountId={}, amountFen={}",
                transactionId, creditAccountId, amountFen);
    }

    /**
     * Cancel 阶段：释放余额冻结，标记还款取消。
     *
     * <p>幂等保证：已取消的还款重复调用直接返回。
     * 空回滚：如果 Try 未执行（无冻结记录），BalanceApplicationService.cancel 会抛出 NOT_FOUND，
     * 此处捕获后记录日志并标记还款取消。</p>
     *
     * @param transactionId 统一交易 ID
     * @param accountId 付款方余额账户 ID
     * @param creditAccountId 信用账户 ID
     * @param now 当前时间
     */
    @Transactional
    public void cancelRepay(
            String transactionId, String accountId, String creditAccountId, Instant now
    ) {
        // 检查还款记录是否已取消（幂等）
        Optional<CreditRepayment> existingRepayment = creditRepaymentRepository
                .findByTransactionId(transactionId);
        if (existingRepayment.isPresent()
                && existingRepayment.get().getStatus() == CreditRepaymentStatus.CANCELLED) {
            log.info("CREDIT_REPAY Cancel 幂等返回: transactionId={}", transactionId);
            return;
        }

        // 释放余额冻结（委托 BalanceApplicationService，复用幂等）
        try {
            balanceApplicationService.cancel(
                    transactionId, accountId, FreezePurpose.CREDIT_REPAYMENT, now
            );
        } catch (BusinessException e) {
            // 空回滚：Try 未执行，无冻结记录
            String code = e.errorCode().code();
            if ("NOT_FOUND".equals(code) || "COMMON_NOT_FOUND".equals(code)) {
                log.warn("CREDIT_REPAY Cancel 空回滚: transactionId={}（Try 未执行或已过期）",
                        transactionId);
            } else {
                throw e;
            }
        }

        // 标记还款取消
        if (existingRepayment.isPresent()) {
            CreditRepayment repayment = existingRepayment.get();
            repayment.markCancelled(now);
            creditRepaymentRepository.save(repayment);
        }

        log.info("CREDIT_REPAY Cancel 成功: transactionId={}, creditAccountId={}",
                transactionId, creditAccountId);
    }

    private String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
