package com.minialalipay.account.application.reconciliation;

import com.minialalipay.account.domain.account.FreezeRecordRepository;
import com.minialalipay.account.domain.account.FreezeStatus;
import com.minialalipay.account.domain.credit.CreditFreeze;
import com.minialalipay.account.domain.credit.CreditFreezeRepository;
import com.minialalipay.account.domain.credit.CreditFreezeStatus;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import com.minialalipay.account.domain.tcc.TccBranchType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 汇总统一交易的余额冻结、TCC 分支与复式账本事实，供终态发布和对账共用。
 *
 * <p>事实核验必须按资金路径选择规则集，否则会把真实一致的交易误判为不一致：
 * <ul>
 *   <li>余额转账：付款方余额冻结（{@code TRANSFER_OUT}）+ 双方余额分支 + {@code LEDGER} 账本分支；</li>
 *   <li>花呗信用支付：信用额度冻结（{@code credit_freeze}）+ 额度分支与收款余额分支
 *       + {@code CREDIT_PAY_LEDGER} 账本分支，不涉及付款方余额冻结。</li>
 * </ul>
 * 识别依据是交易是否存在 {@link TccBranchType#CREDIT_PAY} 账户分支，两种规则集互斥。</p>
 */
@Service
public class TransactionFactApplicationService {
    private final TccBranchRepository branches;
    private final FreezeRecordRepository freezes;
    private final LedgerRepository ledgers;
    private final CreditFreezeRepository creditFreezes;
    public TransactionFactApplicationService(TccBranchRepository branches, FreezeRecordRepository freezes,
                                             LedgerRepository ledgers, CreditFreezeRepository creditFreezes) {
        this.branches = branches; this.freezes = freezes; this.ledgers = ledgers; this.creditFreezes = creditFreezes;
    }

    /** 只读核验成功和取消两套互斥事实，不根据调用超时猜测结果；按资金路径选择对应规则集。 */
    @Transactional(readOnly = true)
    public TransactionFacts inspect(String transactionId) {
        // 存在信用额度分支即花呗支付：它没有付款余额冻结和普通 TRANSFER 账本分支，
        // 套用余额转账规则必然误判事实不一致，必须改用信用规则集。
        if (branches.hasAccountBranch(transactionId, TccBranchType.CREDIT_PAY)) {
            return inspectCreditPay(transactionId);
        }
        // 银行卡提现/充值：无复式账本分支，按各自规则集核验
        if (branches.hasAccountBranch(transactionId, TccBranchType.BANK_CARD_WITHDRAW)) {
            return inspectBankCardWithdraw(transactionId);
        }
        if (branches.hasAccountBranch(transactionId, TccBranchType.BANK_CARD_RECHARGE)) {
            return inspectBankCardRecharge(transactionId);
        }
        return inspectBalanceTransfer(transactionId);
    }

    /** 余额转账事实：付款余额冻结、双方余额分支与 TRANSFER 账本分支全部到位。 */
    private TransactionFacts inspectBalanceTransfer(String transactionId) {
        boolean accountsConfirmed = branches.allAccountBranches(transactionId, TccBranchStatus.CONFIRMED, 2);
        boolean ledgerConfirmed = branches.ledgerBranchIs(transactionId, TccBranchStatus.CONFIRMED);
        boolean freezeConfirmed = freezes.transactionFreezeIs(transactionId, FreezeStatus.CONFIRMED);
        boolean ledgerPosted = ledgers.isPostedAndBalanced(transactionId);
        boolean accountsCancelled = branches.allAccountBranches(transactionId, TccBranchStatus.CANCELLED, 2);
        boolean ledgerCancelled = branches.ledgerBranchIs(transactionId, TccBranchStatus.CANCELLED);
        boolean noActiveFreeze = freezes.transactionHasNoActiveFreeze(transactionId);
        return new TransactionFacts(accountsConfirmed && ledgerConfirmed && freezeConfirmed && ledgerPosted,
                accountsCancelled && ledgerCancelled && noActiveFreeze, accountsConfirmed, ledgerConfirmed,
                freezeConfirmed, ledgerPosted, accountsCancelled, ledgerCancelled, noActiveFreeze);
    }

    /**
     * 花呗信用支付事实：额度冻结、收款余额分支与信用账本分支到位，不涉及付款余额冻结。
     *
     * <p>成功事实要求额度冻结已确认（形成应收与消费明细）；取消事实允许无冻结记录
     * （Try 未实际占用额度即空回滚），但已有记录必须已释放，不得处于冻结或已确认态。</p>
     */
    private TransactionFacts inspectCreditPay(String transactionId) {
        Optional<CreditFreeze> freeze = creditFreezes.findByTransactionId(transactionId);
        // 额度分支（CREDIT_PAY）与收款余额分支（PAYEE_BALANCE）共两个账户分支
        boolean accountsConfirmed = branches.allAccountBranches(transactionId, TccBranchStatus.CONFIRMED, 2);
        boolean ledgerConfirmed = branches.ledgerBranchIs(transactionId, TccBranchType.CREDIT_PAY_LEDGER, TccBranchStatus.CONFIRMED);
        boolean freezeConfirmed = freeze.isPresent() && freeze.get().getStatus() == CreditFreezeStatus.CONFIRMED;
        boolean ledgerPosted = ledgers.isPostedAndBalanced(transactionId);
        boolean accountsCancelled = branches.allAccountBranches(transactionId, TccBranchStatus.CANCELLED, 2);
        boolean ledgerCancelled = branches.ledgerBranchIs(transactionId, TccBranchType.CREDIT_PAY_LEDGER, TccBranchStatus.CANCELLED);
        boolean creditFreezeSettled = freeze.map(f -> f.getStatus() == CreditFreezeStatus.RELEASED).orElse(true);
        boolean noActiveFreeze = creditFreezeSettled && freezes.transactionHasNoActiveFreeze(transactionId);
        return new TransactionFacts(accountsConfirmed && ledgerConfirmed && freezeConfirmed && ledgerPosted,
                accountsCancelled && ledgerCancelled && noActiveFreeze, accountsConfirmed, ledgerConfirmed,
                freezeConfirmed, ledgerPosted, accountsCancelled, ledgerCancelled, noActiveFreeze);
    }

    /**
     * 银行卡提现事实：付款余额冻结 + 银行卡提现分支到位，无复式账本。
     *
     * <p>成功事实要求付款余额冻结已确认（扣减）且银行卡提现分支已确认；
     * 取消事实要求余额冻结已释放且银行卡分支已取消。</p>
     */
    private TransactionFacts inspectBankCardWithdraw(String transactionId) {
        boolean accountsConfirmed = branches.allAccountBranches(transactionId, TccBranchStatus.CONFIRMED, 2);
        boolean freezeConfirmed = freezes.transactionFreezeIs(transactionId, FreezeStatus.CONFIRMED);
        boolean accountsCancelled = branches.allAccountBranches(transactionId, TccBranchStatus.CANCELLED, 2);
        boolean noActiveFreeze = freezes.transactionHasNoActiveFreeze(transactionId);
        // 银行卡提现无复式账本，ledgerConfirmed/ledgerPosted 始终为 true（不需要）
        return new TransactionFacts(accountsConfirmed && freezeConfirmed,
                accountsCancelled && noActiveFreeze, accountsConfirmed, true,
                freezeConfirmed, true, accountsCancelled, true, noActiveFreeze);
    }

    /**
     * 银行卡充值事实：银行卡充值分支 + 收款余额分支到位，无复式账本。
     *
     * <p>成功事实要求银行卡充值分支已确认（扣减）且收款余额分支已确认（入账）；
     * 取消事实要求两个分支均已取消。</p>
     */
    private TransactionFacts inspectBankCardRecharge(String transactionId) {
        boolean accountsConfirmed = branches.allAccountBranches(transactionId, TccBranchStatus.CONFIRMED, 2);
        boolean accountsCancelled = branches.allAccountBranches(transactionId, TccBranchStatus.CANCELLED, 2);
        boolean noActiveFreeze = freezes.transactionHasNoActiveFreeze(transactionId);
        // 银行卡充值无复式账本和无余额冻结，相关字段始终为 true（不需要）
        return new TransactionFacts(accountsConfirmed,
                accountsCancelled && noActiveFreeze, accountsConfirmed, true,
                true, true, accountsCancelled, true, noActiveFreeze);
    }

    /**
     * 终态发布所需的脱敏事实结果。
     *
     * <p>{@code freezeConfirmed} 按资金路径语义不同：余额转账为付款余额冻结已确认扣减，
     * 花呗支付为信用额度冻结已确认占用；{@code noActiveFreeze} 同时要求无活动余额冻结
     * 和无未决信用冻结。</p>
     */
    public record TransactionFacts(boolean successConsistent, boolean cancelConsistent,
            boolean accountsConfirmed, boolean ledgerConfirmed, boolean freezeConfirmed,
            boolean ledgerPosted, boolean accountsCancelled, boolean ledgerCancelled,
            boolean noActiveFreeze) { }
}
