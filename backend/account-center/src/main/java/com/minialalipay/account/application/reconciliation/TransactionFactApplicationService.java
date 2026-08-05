package com.minialalipay.account.application.reconciliation;

import com.minialalipay.account.domain.account.FreezeRecordRepository;
import com.minialalipay.account.domain.account.FreezeStatus;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.tcc.TccBranchRepository;
import com.minialalipay.account.domain.tcc.TccBranchStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 汇总普通转账的余额冻结、TCC 分支与复式账本事实，供终态发布和对账共用。
 */
@Service
public class TransactionFactApplicationService {
    private final TccBranchRepository branches;
    private final FreezeRecordRepository freezes;
    private final LedgerRepository ledgers;
    public TransactionFactApplicationService(TccBranchRepository branches, FreezeRecordRepository freezes,
                                             LedgerRepository ledgers) {
        this.branches = branches; this.freezes = freezes; this.ledgers = ledgers;
    }

    /** 只读核验成功和取消两套互斥事实，不根据调用超时猜测结果。 */
    @Transactional(readOnly = true)
    public TransactionFacts inspect(String transactionId) {
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

    /** 终态发布所需的脱敏事实结果。 */
    public record TransactionFacts(boolean successConsistent, boolean cancelConsistent,
            boolean accountsConfirmed, boolean ledgerConfirmed, boolean freezeConfirmed,
            boolean ledgerPosted, boolean accountsCancelled, boolean ledgerCancelled,
            boolean noActiveFreeze) { }
}
