package com.minialalipay.account.application.tcc;

import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import com.minialalipay.account.domain.ledger.LedgerDirection;
import com.minialalipay.account.domain.ledger.LedgerEntry;
import com.minialalipay.account.domain.ledger.LedgerRepository;
import com.minialalipay.account.domain.ledger.LedgerVoucher;
import com.minialalipay.account.domain.ledger.LedgerVoucherStatus;
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
import java.util.List;

/** 普通转账复式账本 TCC 参与者，凭证准备、过账和取消均在 ledger_db 本地事务内完成。 */
@Service
public class LedgerTccApplicationService {
    private final TccBranchRepository branches;
    private final LedgerRepository ledgers;
    private final LedgerAccountRepository ledgerAccounts;
    private final AccountRepository accounts;

    public LedgerTccApplicationService(TccBranchRepository branches, LedgerRepository ledgers,
            LedgerAccountRepository ledgerAccounts, AccountRepository accounts) {
        this.branches = branches; this.ledgers = ledgers; this.ledgerAccounts = ledgerAccounts; this.accounts = accounts;
    }

    /** Try 创建不可变且平衡的 PREPARED 凭证，不提前过账。 */
    @Transactional
    public TccBranch tryLedger(LedgerCommand c, Instant now) {
        TccBranch branch = find(c);
        if (branch == null) {
            branch = TccBranch.initialize(c.xid(), TccBranchType.LEDGER, c.voucherId(), c.transactionId(), c.amountFen(), now);
            try { branches.createLedgerBranch(branch); }
            catch (DataIntegrityViolationException conflict) { branch = required(c); }
        }
        same(branch, c);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) throw new IllegalStateException("Cancel 已建立屏障，拒绝晚到 Try");
        if (branch.getStatus() != TccBranchStatus.INIT) return branch;
        LedgerVoucher voucher = voucher(c, now);
        LedgerVoucher existing = ledgers.find(c.transactionId(), "TRANSFER", 0).orElse(null);
        if (existing == null) ledgers.savePrepared(voucher);
        else validateVoucher(existing, voucher);
        long version = branch.getBarrierVersion(); branch.markTried(now); save(branch, version); return branch;
    }

    /** Confirm 汇总数据库分录验平后过账，并写账本 Outbox。 */
    @Transactional
    public TccBranch confirmLedger(LedgerCommand c, Instant now) {
        TccBranch branch = required(c); same(branch, c);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) return branch;
        LedgerVoucher voucher = ledgers.find(c.transactionId(), "TRANSFER", 0)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (voucher.getStatus() != LedgerVoucherStatus.POSTED) {
            var totals = ledgers.summarizeEntries(voucher.getVoucherId());
            if (totals.debitFen() != c.amountFen() || totals.creditFen() != c.amountFen()) {
                throw new IllegalStateException("数据库账本分录借贷不平");
            }
            voucher.post(now);
            if (!ledgers.postAndAppendOutbox(voucher, c.eventId(), c.traceId(), now)) {
                throw new IllegalStateException("账本凭证过账并发冲突");
            }
        }
        long version = branch.getBarrierVersion(); branch.confirm(now); save(branch, version); return branch;
    }

    /** Cancel 先到记录空回滚；Try 已完成时只取消 PREPARED 凭证。 */
    @Transactional
    public TccBranch cancelLedger(LedgerCommand c, Instant now) {
        TccBranch branch = find(c);
        if (branch == null) {
            branch = TccBranch.emptyRollback(c.xid(), TccBranchType.LEDGER, c.voucherId(), c.transactionId(), c.amountFen(), now);
            try { branches.createLedgerBranch(branch); return branch; }
            catch (DataIntegrityViolationException conflict) { return required(c); }
        }
        same(branch, c);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) return branch;
        LedgerVoucher voucher = ledgers.find(c.transactionId(), "TRANSFER", 0).orElse(null);
        if (voucher != null && voucher.getStatus() == LedgerVoucherStatus.PREPARED) ledgers.cancelPrepared(voucher.getVoucherId());
        long version = branch.getBarrierVersion(); branch.cancel(now); save(branch, version); return branch;
    }

    private LedgerVoucher voucher(LedgerCommand c, Instant now) {
        var payer = accounts.findById(c.payerAccountId()).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        var payee = accounts.findById(c.payeeAccountId()).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        var payerLedger = ledgerAccounts.findUserBalanceByUserId(payer.getUserId()).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        var payeeLedger = ledgerAccounts.findUserBalanceByUserId(payee.getUserId()).orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<LedgerEntry> entries = List.of(
                new LedgerEntry(c.debitEntryId(), c.voucherId(), c.transactionId(), payerLedger.getLedgerAccountId(), LedgerDirection.DEBIT, c.amountFen(), 1, "普通转账付款", now),
                new LedgerEntry(c.creditEntryId(), c.voucherId(), c.transactionId(), payeeLedger.getLedgerAccountId(), LedgerDirection.CREDIT, c.amountFen(), 2, "普通转账收款", now));
        return LedgerVoucher.prepare(c.voucherId(), c.transactionId(), "TRANSFER", 0, null,
                c.amountFen(), c.amountFen(), entries, now);
    }
    private void validateVoucher(LedgerVoucher left, LedgerVoucher right) {
        if (left.getTotalDebitFen() != right.getTotalDebitFen() || left.getTotalCreditFen() != right.getTotalCreditFen()) {
            throw new IllegalStateException("相同交易对应的账本参数不一致");
        }
    }
    private TccBranch find(LedgerCommand c) { return branches.findLedgerBranchForUpdate(c.xid(), c.voucherId()).orElse(null); }
    private TccBranch required(LedgerCommand c) {
        TccBranch branch = find(c); if (branch == null) throw new BusinessException(CommonErrorCode.NOT_FOUND); return branch;
    }
    private void same(TccBranch b, LedgerCommand c) {
        if (!b.getTransactionId().equals(c.transactionId()) || b.getAmountFen() != c.amountFen()) throw new IllegalStateException("TCC 分支幂等参数不一致");
    }
    private void save(TccBranch b, long version) {
        if (!branches.updateLedgerBranch(b, version)) throw new IllegalStateException("TCC 分支版本冲突");
    }

    /** 账本参与者命令；两个分录 ID 必须稳定重试。 */
    public record LedgerCommand(String xid, String transactionId, String payerAccountId, String payeeAccountId,
            long amountFen, String voucherId, long debitEntryId, long creditEntryId, String eventId, String traceId) { }
}
