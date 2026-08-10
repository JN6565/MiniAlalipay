package com.minialalipay.account.application.tcc;

import com.minialalipay.account.domain.account.AccountErrorCode;
import com.minialalipay.account.domain.bankcard.BankCard;
import com.minialalipay.account.domain.bankcard.BankCardErrorCode;
import com.minialalipay.account.domain.bankcard.BankCardRepository;
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

/**
 * 银行卡余额 TCC 应用服务：处理充值和提现的 Try/Confirm/Cancel 三阶段。
 *
 * <p>充值（银行卡给账户充钱）：Try 建立屏障并校验余额充足，Confirm 扣减银行卡虚拟余额；
 * 提现（账户给银行卡充钱）：Try 建立屏障并校验银行卡归属，Confirm 增加银行卡虚拟余额。</p>
 *
 * <p>每个动作在 account_db 本地事务内同时推进屏障和余额事实。Cancel 先到必须留下
 * EMPTY 屏障；晚到 Try 不能再操作余额。</p>
 */
@Service
public class BankCardBalanceTccApplicationService {

    private final BankCardRepository bankCardRepository;
    private final TccBranchRepository tccBranchRepository;

    public BankCardBalanceTccApplicationService(BankCardRepository bankCardRepository,
                                                 TccBranchRepository tccBranchRepository) {
        this.bankCardRepository = bankCardRepository;
        this.tccBranchRepository = tccBranchRepository;
    }

    /** 充值 Try（银行卡给账户充钱）：建立屏障、校验归属和余额充足，Confirm 扣减银行卡虚拟余额。 */
    @Transactional
    public TccBranch tryRecharge(Command command, Instant now) {
        TccBranch branch = initialize(command, TccBranchType.BANK_CARD_RECHARGE, now);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("Cancel 已建立屏障，拒绝晚到 Try");
        }
        if (branch.getStatus() != TccBranchStatus.INIT) return sameRequest(branch, command);
        BankCard card = requireCardOwner(command);
        // 充值从银行卡出资金，必须校验余额充足
        if (card.getBalanceFen() < command.amountFen()) {
            throw new BusinessException(BankCardErrorCode.BANK_CARD_INSUFFICIENT_BALANCE);
        }
        long version = branch.getBarrierVersion();
        branch.markTried(now);
        save(branch, version);
        return branch;
    }

    /** 充值 Confirm（银行卡给账户充钱）：扣减银行卡虚拟余额，相同屏障重复调用只扣减一次。 */
    @Transactional
    public TccBranch confirmRecharge(Command command, Instant now) {
        TccBranch branch = required(command, TccBranchType.BANK_CARD_RECHARGE);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) return branch;
        BankCard card = requireCard(command.cardId());
        long expected = card.getVersion();
        // 充值从银行卡出资金，Confirm 扣减银行卡虚拟余额
        card.withdraw(command.amountFen(), now);
        if (!bankCardRepository.updateBalanceByCas(card, expected)) {
            throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        }
        long version = branch.getBarrierVersion();
        branch.confirm(now);
        save(branch, version);
        return branch;
    }

    /** 充值 Cancel：释放屏障；Try 未到时仅记录空回滚。 */
    @Transactional
    public TccBranch cancelRecharge(Command command, Instant now) {
        TccBranch branch = find(command, TccBranchType.BANK_CARD_RECHARGE);
        if (branch == null) return emptyRollback(command, TccBranchType.BANK_CARD_RECHARGE, now);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) return branch;
        long version = branch.getBarrierVersion();
        branch.cancel(now);
        save(branch, version);
        return branch;
    }

    /** 提现 Try（账户给银行卡充钱）：建立屏障并校验银行卡归属，Confirm 增加银行卡虚拟余额。 */
    @Transactional
    public TccBranch tryWithdraw(Command command, Instant now) {
        TccBranch branch = initialize(command, TccBranchType.BANK_CARD_WITHDRAW, now);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) {
            throw new IllegalStateException("Cancel 已建立屏障，拒绝晚到 Try");
        }
        if (branch.getStatus() != TccBranchStatus.INIT) return sameRequest(branch, command);
        requireCardOwner(command);
        long version = branch.getBarrierVersion();
        branch.markTried(now);
        save(branch, version);
        return branch;
    }

    /** 提现 Confirm（账户给银行卡充钱）：增加银行卡虚拟余额，相同屏障重复调用只充值一次。 */
    @Transactional
    public TccBranch confirmWithdraw(Command command, Instant now) {
        TccBranch branch = required(command, TccBranchType.BANK_CARD_WITHDRAW);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CONFIRMED) return branch;
        BankCard card = requireCard(command.cardId());
        long expected = card.getVersion();
        // 提现向银行卡入账，Confirm 增加银行卡虚拟余额
        card.recharge(command.amountFen(), now);
        if (!bankCardRepository.updateBalanceByCas(card, expected)) {
            throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        }
        long version = branch.getBarrierVersion();
        branch.confirm(now);
        save(branch, version);
        return branch;
    }

    /** 提现 Cancel：释放屏障；Try 未到时仅记录空回滚。 */
    @Transactional
    public TccBranch cancelWithdraw(Command command, Instant now) {
        TccBranch branch = find(command, TccBranchType.BANK_CARD_WITHDRAW);
        if (branch == null) return emptyRollback(command, TccBranchType.BANK_CARD_WITHDRAW, now);
        sameRequest(branch, command);
        if (branch.getStatus() == TccBranchStatus.CANCELLED) return branch;
        long version = branch.getBarrierVersion();
        branch.cancel(now);
        save(branch, version);
        return branch;
    }

    // ===== 内部辅助 =====

    private BankCard requireCard(String cardId) {
        return bankCardRepository.findById(cardId)
                .orElseThrow(() -> new BusinessException(BankCardErrorCode.BANK_CARD_NOT_FOUND));
    }

    private BankCard requireCardOwner(Command command) {
        BankCard card = requireCard(command.cardId());
        if (!card.getUserId().equals(command.userId())) {
            throw new BusinessException(BankCardErrorCode.BANK_CARD_NOT_FOUND);
        }
        return card;
    }

    private TccBranch initialize(Command c, TccBranchType type, Instant now) {
        TccBranch existing = find(c, type);
        if (existing != null) return sameRequest(existing, c);
        TccBranch created = TccBranch.initialize(c.xid(), type, c.cardId(), c.transactionId(), c.amountFen(), now);
        try { tccBranchRepository.createAccountBranch(created); return created; }
        catch (DataIntegrityViolationException conflict) { return required(c, type); }
    }

    private TccBranch emptyRollback(Command c, TccBranchType type, Instant now) {
        TccBranch branch = TccBranch.emptyRollback(c.xid(), type, c.cardId(), c.transactionId(), c.amountFen(), now);
        try { tccBranchRepository.createAccountBranch(branch); return branch; }
        catch (DataIntegrityViolationException conflict) { return required(c, type); }
    }

    private TccBranch required(Command c, TccBranchType type) {
        TccBranch value = find(c, type);
        if (value == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        return value;
    }

    private TccBranch find(Command c, TccBranchType type) {
        return tccBranchRepository.findAccountBranchForUpdate(c.xid(), type, c.cardId()).orElse(null);
    }

    private TccBranch sameRequest(TccBranch branch, Command c) {
        if (!branch.getTransactionId().equals(c.transactionId()) || branch.getAmountFen() != c.amountFen()) {
            throw new BusinessException(AccountErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return branch;
    }

    private void save(TccBranch branch, long version) {
        if (!tccBranchRepository.updateAccountBranch(branch, version)) {
            throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        }
    }

    /** 银行卡充值/提现命令；所有金额单位为分。 */
    public record Command(String xid, String transactionId, String userId, String cardId, long amountFen) { }
}
