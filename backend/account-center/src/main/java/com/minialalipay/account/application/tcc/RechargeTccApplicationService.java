package com.minialalipay.account.application.tcc;

import com.minialalipay.account.domain.account.AccountBalance;
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

/**
 * 充值专用 TCC 参与者。
 *
 * <p>Try 只验证目标账户并创建 PREPARED 凭证；Confirm 在同一数据库事务中增加余额、过账并推进屏障，
 * 从而保证余额事实与复式账本事实同时成功或同时回滚。Cancel 只允许取消尚未过账的凭证。</p>
 */
@Service
public class RechargeTccApplicationService {
    private final TccBranchRepository branches;
    private final AccountRepository accounts;
    private final LedgerAccountRepository ledgerAccounts;
    private final LedgerRepository ledgers;

    public RechargeTccApplicationService(TccBranchRepository branches, AccountRepository accounts,
                                         LedgerAccountRepository ledgerAccounts, LedgerRepository ledgers) {
        this.branches = branches;
        this.accounts = accounts;
        this.ledgerAccounts = ledgerAccounts;
        this.ledgers = ledgers;
    }

    /** 执行充值 Try；重复同载荷请求幂等，Cancel 先到时拒绝悬挂。 */
    @Transactional
    public TccBranch tryRecharge(Command command, Instant now) {
        TccBranch branch = initialize(command, now);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("充值已取消，拒绝迟到 Try");
        }
        if (branch.getStatus() != TccBranchStatus.INIT) return branch;
        var account = accounts.findById(command.targetAccountId())
                .filter(value -> "ACTIVE".equals(value.getStatus().name()))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        var systemIssuance = ledgerAccounts.findSystemIssuance()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        var userBalance = ledgerAccounts.findUserBalanceByUserId(account.getUserId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        LedgerVoucher expected = LedgerVoucher.prepare(command.voucherId(), command.transactionId(), "RECHARGE", 0,
                null, command.amountFen(), command.amountFen(), List.of(
                        new LedgerEntry(command.debitEntryId(), command.voucherId(), command.transactionId(),
                                systemIssuance.getLedgerAccountId(), LedgerDirection.DEBIT, command.amountFen(), 1,
                                "充值借记系统发行权益", now),
                        new LedgerEntry(command.creditEntryId(), command.voucherId(), command.transactionId(),
                                userBalance.getLedgerAccountId(), LedgerDirection.CREDIT, command.amountFen(), 2,
                                "充值贷记用户余额负债", now)), now);
        LedgerVoucher existing = ledgers.find(command.transactionId(), "RECHARGE", 0).orElse(null);
        if (existing == null) ledgers.savePrepared(expected); else validateVoucher(existing, expected);
        long version = branch.getBarrierVersion();
        branch.markTried(now);
        save(branch, version);
        return branch;
    }

    /** 执行充值 Confirm；屏障终态保证余额只增加一次。 */
    @Transactional
    public TccBranch confirmRecharge(Command command, Instant now) {
        TccBranch branch = required(command);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) return branch;
        if (branch.getStatus() != TccBranchStatus.TRIED) throw new IllegalStateException("充值分支尚未完成 Try");
        AccountBalance balance = accounts.findBalance(command.targetAccountId())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        long balanceVersion = balance.getVersion();
        balance.credit(command.amountFen(), now);
        if (!accounts.updateBalanceForActiveAccount(balance, balanceVersion)) throw new IllegalStateException("充值余额版本冲突");
        LedgerVoucher voucher = ledgers.find(command.transactionId(), "RECHARGE", 0)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        if (voucher.getStatus() != LedgerVoucherStatus.POSTED) {
            var totals = ledgers.summarizeEntries(voucher.getVoucherId());
            if (totals.debitFen() != command.amountFen() || totals.creditFen() != command.amountFen()) {
                throw new IllegalStateException("充值账本分录借贷不平");
            }
            voucher.post(now);
            if (!ledgers.postAndAppendOutbox(voucher, command.eventId(), command.traceId(), now)) {
                throw new IllegalStateException("充值凭证过账并发冲突");
            }
        }
        long branchVersion = branch.getBarrierVersion();
        branch.confirm(now);
        save(branch, branchVersion);
        return branch;
    }

    /** 执行充值 Cancel；Try 未到时建立空回滚屏障，防止后续误入账。 */
    @Transactional
    public TccBranch cancelRecharge(Command command, Instant now) {
        TccBranch branch = find(command);
        if (branch == null) return emptyRollback(command, now);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) return branch;
        ledgers.find(command.transactionId(), "RECHARGE", 0).ifPresent(voucher -> {
            if (voucher.getStatus() == LedgerVoucherStatus.PREPARED) ledgers.cancelPrepared(voucher.getVoucherId());
        });
        long version = branch.getBarrierVersion();
        branch.cancel(now);
        save(branch, version);
        return branch;
    }

    private TccBranch initialize(Command c, Instant now) {
        TccBranch existing = find(c);
        if (existing != null) return existing;
        TccBranch created = TccBranch.initialize(c.xid(), TccBranchType.RECHARGE, c.targetAccountId(), c.transactionId(), c.amountFen(), now);
        try { branches.createAccountBranch(created); return created; }
        catch (DataIntegrityViolationException conflict) { return required(c); }
    }
    private TccBranch emptyRollback(Command c, Instant now) {
        TccBranch branch = TccBranch.emptyRollback(c.xid(), TccBranchType.RECHARGE, c.targetAccountId(), c.transactionId(), c.amountFen(), now);
        try { branches.createAccountBranch(branch); return branch; }
        catch (DataIntegrityViolationException conflict) { return required(c); }
    }
    private TccBranch find(Command c) { return branches.findAccountBranchForUpdate(c.xid(), TccBranchType.RECHARGE, c.targetAccountId()).orElse(null); }
    private TccBranch required(Command c) { TccBranch branch = find(c); if (branch == null) throw new BusinessException(CommonErrorCode.NOT_FOUND); return branch; }
    private void sameRequest(TccBranch b, Command c) { if (!b.getTransactionId().equals(c.transactionId()) || b.getAmountFen() != c.amountFen()) throw new IllegalStateException("充值 TCC 幂等参数不一致"); }
    private void save(TccBranch b, long version) { if (!branches.updateAccountBranch(b, version)) throw new IllegalStateException("充值 TCC 屏障版本冲突"); }
    private void validateVoucher(LedgerVoucher left, LedgerVoucher right) { if (left.getTotalDebitFen() != right.getTotalDebitFen() || left.getTotalCreditFen() != right.getTotalCreditFen()) throw new IllegalStateException("充值凭证参数不一致"); }

    /** 充值 TCC 命令；金额单位为分，全部标识必须在重试时保持稳定。 */
    public record Command(String xid, String transactionId, String targetAccountId, long amountFen,
                          String voucherId, long debitEntryId, long creditEntryId, String eventId, String traceId) { }
}
