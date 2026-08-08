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
 * 退款专用复式账本 TCC 参与者。
 *
 * <p>该服务只写 {@code ledger_db} 的账本分支、凭证、分录和 Outbox。原收款方（商户）始终借记其
 * 余额负债科目；余额退款贷记原付款人余额负债科目，信用退款贷记原付款人信用应收资产科目。
 * 凭证类型固定为 {@code REFUND}，不得复用普通 {@code TRANSFER} 账本分支。</p>
 */
@Service
public class RefundLedgerTccApplicationService {

    private static final String VOUCHER_TYPE = "REFUND";

    private final TccBranchRepository branches;
    private final LedgerRepository ledgers;
    private final LedgerAccountRepository ledgerAccounts;
    private final AccountRepository accounts;

    public RefundLedgerTccApplicationService(TccBranchRepository branches, LedgerRepository ledgers,
                                             LedgerAccountRepository ledgerAccounts, AccountRepository accounts) {
        this.branches = branches;
        this.ledgers = ledgers;
        this.ledgerAccounts = ledgerAccounts;
        this.accounts = accounts;
    }

    /**
     * 创建不可变的退款预记账凭证。
     *
     * <p>同一 {@code xid + voucherId} 重试必须携带完全一致的商户、付款、信用账户、金额、凭证、分录和事件标识。
     * Cancel 已先到时写入空回滚屏障，晚到 Try 必须失败，防止悬挂预记账。</p>
     */
    @Transactional
    public TccBranch tryLedger(RefundLedgerCommand command, Instant now) {
        TccBranch branch = find(command);
        if (branch == null) {
            branch = TccBranch.initialize(command.xid(), TccBranchType.REFUND_LEDGER,
                    command.voucherId(), command.transactionId(), command.amountFen(), now);
            try {
                branches.createLedgerBranch(branch);
            } catch (DataIntegrityViolationException conflict) {
                branch = required(command);
            }
        }
        same(branch, command);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("Cancel 已建立退款账本屏障，拒绝晚到 Try");
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
     * <p>Confirm 超时不能推导为失败，由全局协调器用相同技术键重试；本方法只接受已 Try 的退款账本分支。</p>
     */
    @Transactional
    public TccBranch confirmLedger(RefundLedgerCommand command, Instant now) {
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
                throw new IllegalStateException("退款账本分录借贷不平");
            }
            voucher.post(now);
            if (!ledgers.postAndAppendOutbox(voucher, command.eventId(), command.traceId(), now)) {
                throw new IllegalStateException("退款账本凭证过账并发冲突");
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
     * <p>已过账凭证不能直接取消，必须进入受控冲正流程。</p>
     */
    @Transactional
    public TccBranch cancelLedger(RefundLedgerCommand command, Instant now) {
        TccBranch branch = find(command);
        if (branch == null) {
            branch = TccBranch.emptyRollback(command.xid(), TccBranchType.REFUND_LEDGER,
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

    private LedgerVoucher voucher(RefundLedgerCommand command, Instant now) {
        var merchant = accounts.findById(command.merchantAccountId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        LedgerAccount merchantBalance = ledgerAccounts.findUserBalanceByUserId(merchant.getUserId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        List<LedgerEntry> entries;
        if (command.creditAccountId() != null) {
            LedgerAccount receivable = ledgerAccounts.findCreditReceivableByCreditAccountId(command.creditAccountId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
            entries = List.of(
                    new LedgerEntry(command.debitEntryId(), command.voucherId(), command.transactionId(),
                            merchantBalance.getLedgerAccountId(), LedgerDirection.DEBIT, command.amountFen(), 1,
                            "受控退款：减少原收款方余额", now),
                    new LedgerEntry(command.creditEntryId(), command.voucherId(), command.transactionId(),
                            receivable.getLedgerAccountId(), LedgerDirection.CREDIT, command.amountFen(), 2,
                            "受控退款：核销信用应收资产", now));
        } else {
            var payer = accounts.findById(command.payerAccountId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
            LedgerAccount payerBalance = ledgerAccounts.findUserBalanceByUserId(payer.getUserId())
                    .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
            entries = List.of(
                    new LedgerEntry(command.debitEntryId(), command.voucherId(), command.transactionId(),
                            merchantBalance.getLedgerAccountId(), LedgerDirection.DEBIT, command.amountFen(), 1,
                            "受控退款：减少原收款方余额", now),
                    new LedgerEntry(command.creditEntryId(), command.voucherId(), command.transactionId(),
                            payerBalance.getLedgerAccountId(), LedgerDirection.CREDIT, command.amountFen(), 2,
                            "受控退款：增加原付款人余额", now));
        }
        return LedgerVoucher.prepare(command.voucherId(), command.transactionId(), VOUCHER_TYPE, 0, null,
                command.amountFen(), command.amountFen(), entries, now);
    }

    private void validateVoucher(LedgerVoucher existing, LedgerVoucher expected) {
        if (!VOUCHER_TYPE.equals(existing.getVoucherType())
                || existing.getTotalDebitFen() != expected.getTotalDebitFen()
                || existing.getTotalCreditFen() != expected.getTotalCreditFen()
                || existing.getEntries().size() != expected.getEntries().size()) {
            throw new IllegalStateException("同一退款交易的账本参数不一致");
        }
        for (int index = 0; index < expected.getEntries().size(); index++) {
            LedgerEntry left = existing.getEntries().get(index);
            LedgerEntry right = expected.getEntries().get(index);
            if (left.entryId() != right.entryId() || !left.ledgerAccountId().equals(right.ledgerAccountId())
                    || left.direction() != right.direction() || left.amountFen() != right.amountFen()
                    || left.sequenceNo() != right.sequenceNo()) {
                throw new IllegalStateException("同一退款交易的账本分录不一致");
            }
        }
    }

    private TccBranch find(RefundLedgerCommand command) {
        return branches.findLedgerBranchForUpdate(command.xid(), TccBranchType.REFUND_LEDGER,
                command.voucherId()).orElse(null);
    }

    private TccBranch required(RefundLedgerCommand command) {
        TccBranch branch = find(command);
        if (branch == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return branch;
    }

    private void same(TccBranch branch, RefundLedgerCommand command) {
        if (!branch.getTransactionId().equals(command.transactionId()) || branch.getAmountFen() != command.amountFen()) {
            throw new IllegalStateException("退款账本 TCC 分支幂等参数不一致");
        }
    }

    private void save(TccBranch branch, long expectedVersion) {
        if (!branches.updateLedgerBranch(branch, expectedVersion)) {
            throw new IllegalStateException("退款账本 TCC 分支版本冲突");
        }
    }

    /**
     * 退款账本分支命令；所有标识都由业务中心从统一 REFUND 交易稳定派生。
     *
     * @param merchantAccountId 退款发起人（原收款方）账户，借方
     * @param payerAccountId 原付款人余额账户，余额退款时贷方
     * @param creditAccountId 原付款人信用账户，信用退款时以其应收资产科目为贷方
     */
    public record RefundLedgerCommand(String xid, String transactionId, String merchantAccountId,
                                      String payerAccountId, String creditAccountId, long amountFen,
                                      String voucherId, long debitEntryId, long creditEntryId,
                                      String eventId, String traceId) {
    }
}
