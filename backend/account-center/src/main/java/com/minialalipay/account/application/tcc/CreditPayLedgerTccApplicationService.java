package com.minialalipay.account.application.tcc;

import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.ledger.LedgerAccount;
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

/**
 * 信用支付专用的复式账本 TCC 参与者。
 *
 * <p>该服务只写 {@code ledger_db} 的账本分支、凭证、分录和 Outbox。付款信用账户不是余额账户，
 * 因此固定生成“借记信用应收资产、贷记收款用户余额负债”的 {@code CREDIT_PAY} 凭证；
 * 不允许调用方传入付款余额账户，也不得复用普通 {@code TRANSFER} 账本分支。</p>
 */
@Service
public class CreditPayLedgerTccApplicationService {

    private static final String VOUCHER_TYPE = "CREDIT_PAY";

    private final TccBranchRepository branches;
    private final LedgerRepository ledgers;
    private final LedgerAccountRepository ledgerAccounts;
    private final AccountRepository accounts;

    public CreditPayLedgerTccApplicationService(TccBranchRepository branches, LedgerRepository ledgers,
                                                LedgerAccountRepository ledgerAccounts, AccountRepository accounts) {
        this.branches = branches;
        this.ledgers = ledgers;
        this.ledgerAccounts = ledgerAccounts;
        this.accounts = accounts;
    }

    /**
     * 创建不可变的信用支付预记账凭证。
     *
     * <p>同一 {@code xid + voucherId} 重试必须携带完全一致的信用账户、收款账户、金额、凭证、分录和事件标识。
     * Cancel 已先到时写入空回滚屏障，晚到 Try 必须失败，防止悬挂预记账。</p>
     */
    @Transactional
    public TccBranch tryLedger(CreditPayLedgerCommand command, Instant now) {
        TccBranch branch = find(command);
        if (branch == null) {
            branch = TccBranch.initialize(command.xid(), TccBranchType.CREDIT_PAY_LEDGER,
                    command.voucherId(), command.transactionId(), command.amountFen(), now);
            try {
                branches.createLedgerBranch(branch);
            } catch (DataIntegrityViolationException conflict) {
                branch = required(command);
            }
        }
        same(branch, command);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("Cancel 已建立信用账本屏障，拒绝晚到 Try");
        }
        if (branch.getStatus() != TccBranchStatus.INIT) {
            return branch;
        }

        LedgerVoucher expected = voucher(command, now);
        LedgerVoucher existing = ledgers.find(command.transactionId(), VOUCHER_TYPE, 0).orElse(null);
        if (existing == null) {
            ledgers.savePrepared(expected);
        } else {
            validateVoucher(existing, expected);
        }
        long version = branch.getBarrierVersion();
        branch.markTried(now);
        save(branch, version);
        return branch;
    }

    /**
     * 校验数据库实际分录平衡后过账，并与账本 Outbox 在同一事务提交。
     *
     * <p>Confirm 超时不能推导为失败，由全局协调器用相同技术键重试；本方法只接受已 Try 的信用账本分支。</p>
     */
    @Transactional
    public TccBranch confirmLedger(CreditPayLedgerCommand command, Instant now) {
        TccBranch branch = required(command);
        same(branch, command);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) {
            return branch;
        }
        LedgerVoucher voucher = ledgers.find(command.transactionId(), VOUCHER_TYPE, 0)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (voucher.getStatus() != LedgerVoucherStatus.POSTED) {
            var totals = ledgers.summarizeEntries(voucher.getVoucherId());
            if (totals.debitFen() != command.amountFen() || totals.creditFen() != command.amountFen()) {
                throw new IllegalStateException("信用支付账本分录借贷不平");
            }
            voucher.post(now);
            if (!ledgers.postAndAppendOutbox(voucher, command.eventId(), command.traceId(), now)) {
                throw new IllegalStateException("信用支付账本凭证过账并发冲突");
            }
        }
        long version = branch.getBarrierVersion();
        branch.confirm(now);
        save(branch, version);
        return branch;
    }

    /**
     * 取消预记账凭证，或在 Try 尚未到达时建立空回滚屏障。
     *
     * <p>已过账凭证不能直接取消，必须进入受控冲正流程；这样不会在已增加收款余额后删除信用应收账本事实。</p>
     */
    @Transactional
    public TccBranch cancelLedger(CreditPayLedgerCommand command, Instant now) {
        TccBranch branch = find(command);
        if (branch == null) {
            branch = TccBranch.emptyRollback(command.xid(), TccBranchType.CREDIT_PAY_LEDGER,
                    command.voucherId(), command.transactionId(), command.amountFen(), now);
            try {
                branches.createLedgerBranch(branch);
                return branch;
            } catch (DataIntegrityViolationException conflict) {
                return required(command);
            }
        }
        same(branch, command);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            return branch;
        }
        LedgerVoucher voucher = ledgers.find(command.transactionId(), VOUCHER_TYPE, 0).orElse(null);
        if (voucher != null && voucher.getStatus() == LedgerVoucherStatus.PREPARED) {
            ledgers.cancelPrepared(voucher.getVoucherId());
        }
        long version = branch.getBarrierVersion();
        branch.cancel(now);
        save(branch, version);
        return branch;
    }

    private LedgerVoucher voucher(CreditPayLedgerCommand command, Instant now) {
        LedgerAccount receivable = ledgerAccounts
                .findCreditReceivableByCreditAccountId(command.creditAccountId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        var payee = accounts.findById(command.payeeAccountId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        LedgerAccount payeeBalance = ledgerAccounts.findUserBalanceByUserId(payee.getUserId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<LedgerEntry> entries = List.of(
                new LedgerEntry(command.debitEntryId(), command.voucherId(), command.transactionId(),
                        receivable.getLedgerAccountId(), LedgerDirection.DEBIT, command.amountFen(), 1,
                        "信用支付确认：增加信用应收", now),
                new LedgerEntry(command.creditEntryId(), command.voucherId(), command.transactionId(),
                        payeeBalance.getLedgerAccountId(), LedgerDirection.CREDIT, command.amountFen(), 2,
                        "信用支付确认：增加收款用户余额负债", now));
        return LedgerVoucher.prepare(command.voucherId(), command.transactionId(), VOUCHER_TYPE, 0, null,
                command.amountFen(), command.amountFen(), entries, now);
    }

    private void validateVoucher(LedgerVoucher existing, LedgerVoucher expected) {
        if (!VOUCHER_TYPE.equals(existing.getVoucherType())
                || existing.getTotalDebitFen() != expected.getTotalDebitFen()
                || existing.getTotalCreditFen() != expected.getTotalCreditFen()
                || existing.getEntries().size() != expected.getEntries().size()) {
            throw new IllegalStateException("同一信用支付交易的账本参数不一致");
        }
        for (int index = 0; index < expected.getEntries().size(); index++) {
            LedgerEntry left = existing.getEntries().get(index);
            LedgerEntry right = expected.getEntries().get(index);
            if (left.entryId() != right.entryId() || !left.ledgerAccountId().equals(right.ledgerAccountId())
                    || left.direction() != right.direction() || left.amountFen() != right.amountFen()
                    || left.sequenceNo() != right.sequenceNo()) {
                throw new IllegalStateException("同一信用支付交易的账本分录不一致");
            }
        }
    }

    private TccBranch find(CreditPayLedgerCommand command) {
        return branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.CREDIT_PAY_LEDGER,
                command.voucherId()).orElse(null);
    }

    private TccBranch required(CreditPayLedgerCommand command) {
        TccBranch branch = find(command);
        if (branch == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return branch;
    }

    private void same(TccBranch branch, CreditPayLedgerCommand command) {
        if (!branch.getTransactionId().equals(command.transactionId()) || branch.getAmountFen() != command.amountFen()) {
            throw new IllegalStateException("信用支付账本 TCC 分支幂等参数不一致");
        }
    }

    private void save(TccBranch branch, long expectedVersion) {
        if (!branches.updateLedgerBranch(branch, expectedVersion)) {
            throw new IllegalStateException("信用支付账本 TCC 分支版本冲突");
        }
    }

    /**
     * 信用支付账本分支命令；所有标识都由业务中心从统一交易稳定派生。
     *
     * @param creditAccountId 付款人的信用账户，不是余额账户
     * @param payeeAccountId 收款用户的普通余额账户
     */
    public record CreditPayLedgerCommand(String xid, String transactionId, String creditAccountId,
                                         String payeeAccountId, long amountFen, String voucherId,
                                         long debitEntryId, long creditEntryId, String eventId, String traceId) {
    }
}
