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
import com.minialalipay.common.error.BusinessException;
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

    public CreditTccParticipant(
            CreditAccountRepository creditAccountRepository,
            CreditFreezeRepository creditFreezeRepository,
            CreditPurchaseRepository creditPurchaseRepository,
            CreditReceivableRepository creditReceivableRepository
    ) {
        this.creditAccountRepository = creditAccountRepository;
        this.creditFreezeRepository = creditFreezeRepository;
        this.creditPurchaseRepository = creditPurchaseRepository;
        this.creditReceivableRepository = creditReceivableRepository;
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
        // 幂等检查 + 防悬挂
        Optional<CreditFreeze> existing = creditFreezeRepository
                .findByTransactionIdAndAccountId(transactionId, creditAccountId);
        if (existing.isPresent()) {
            CreditFreeze freeze = existing.get();
            if (freeze.getStatus() == CreditFreezeStatus.RELEASED) {
                // 防悬挂：Cancel 已执行，拒绝 Try
                throw new BusinessException(CreditErrorCode.CREDIT_NOT_AVAILABLE);
            }
            // 幂等：参数一致时返回已有记录
            validateRepeatedFreeze(freeze, amountFen, branchXid);
            return freeze;
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
     * @param qrOrderId 扫码订单 ID（用于创建消费明细）
     * @param merchantAccountId 收款方账户 ID（用于创建消费明细）
     * @param now 当前时间
     * @throws BusinessException 冻结记录不存在
     * @throws IllegalStateException 冻结记录已释放
     */
    @Transactional
    public void confirmFreeze(
            String transactionId, String creditAccountId,
            String qrOrderId, String merchantAccountId, Instant now
    ) {
        CreditFreeze freeze = creditFreezeRepository
                .findByTransactionIdAndAccountId(transactionId, creditAccountId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));

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

        log.info("CREDIT_PAY Confirm 成功: transactionId={}, creditAccountId={}, amountFen={}",
                transactionId, creditAccountId, freeze.getAmountFen());
    }

    /**
     * Cancel 阶段：释放冻结额度。
     *
     * <p>幂等保证：已释放的冻结记录重复调用直接返回。
     * 空回滚：冻结记录不存在时记录日志并快速返回。</p>
     *
     * @param transactionId 统一交易 ID
     * @param creditAccountId 信用账户 ID
     * @param now 当前时间
     * @throws BusinessException 信用账户不存在（有冻结记录时）
     * @throws IllegalStateException 冻结记录已确认
     */
    @Transactional
    public void cancelFreeze(
            String transactionId, String creditAccountId, Instant now
    ) {
        Optional<CreditFreeze> existing = creditFreezeRepository
                .findByTransactionIdAndAccountId(transactionId, creditAccountId);

        // 空回滚：Try 未执行，记录日志并返回
        if (existing.isEmpty()) {
            log.warn("CREDIT_PAY Cancel 空回滚: transactionId={}, creditAccountId={}（Try 未执行或已过期）",
                    transactionId, creditAccountId);
            return;
        }

        CreditFreeze freeze = existing.get();

        // 幂等：已释放直接返回
        if (freeze.getStatus() == CreditFreezeStatus.RELEASED) {
            log.info("CREDIT_PAY Cancel 幂等返回: transactionId={}", transactionId);
            return;
        }

        // 已确认的冻结不可释放
        if (freeze.getStatus() == CreditFreezeStatus.CONFIRMED) {
            throw new IllegalStateException("已确认的冻结记录不可释放: " + transactionId);
        }

        CreditAccount account = creditAccountRepository.findById(creditAccountId)
                .orElseThrow(() -> new BusinessException(CreditErrorCode.CREDIT_ACCOUNT_NOT_FOUND));

        account.releaseFreeze(freeze.getAmountFen(), now);
        creditAccountRepository.save(account);

        freeze.release(now);
        creditFreezeRepository.save(freeze);

        log.info("CREDIT_PAY Cancel 成功: transactionId={}, creditAccountId={}, amountFen={}",
                transactionId, creditAccountId, freeze.getAmountFen());
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
