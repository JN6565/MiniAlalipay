package com.minialalipay.account.application.account;

import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.AccountErrorCode;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.account.AccountStatus;
import com.minialalipay.account.domain.account.FreezePurpose;
import com.minialalipay.account.domain.account.FreezeRecord;
import com.minialalipay.account.domain.account.FreezeRecordRepository;
import com.minialalipay.account.domain.account.FreezeStatus;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

/**
 * 余额冻结、确认和释放应用服务，为后续 TCC 分支提供本地事务入口。
 *
 * <p>余额变化与冻结事实必须同事务提交。重复同向动作只返回既有记录；CAS 失败必须整体回滚，
 * 防止余额与分支事实出现一边成功、一边失败。</p>
 */
@Service
public class BalanceApplicationService {

    private final AccountRepository accountRepository;
    private final FreezeRecordRepository freezeRecordRepository;

    public BalanceApplicationService(AccountRepository accountRepository,
                                     FreezeRecordRepository freezeRecordRepository) {
        this.accountRepository = accountRepository;
        this.freezeRecordRepository = freezeRecordRepository;
    }

    /** 执行 Try 冻结；同一业务键重复调用保持幂等。 */
    @Transactional
    public FreezeRecord freeze(String freezeId, String transactionId, String accountId, FreezePurpose purpose,
                               long amountFen, String branchXid, Instant now) {
        FreezeRecord existing = freezeRecordRepository.find(transactionId, accountId, purpose).orElse(null);
        if (existing != null) {
            return validateRepeatedFreeze(existing, amountFen, branchXid);
        }
        FreezeRecord record = FreezeRecord.create(freezeId, transactionId, accountId, purpose,
                amountFen, branchXid, now);
        try {
            // 唯一业务键必须先成为幂等屏障；后续余额 CAS 失败会由本地事务整体回滚该记录。
            freezeRecordRepository.create(record);
        } catch (DataIntegrityViolationException conflict) {
            FreezeRecord committed = freezeRecordRepository.find(transactionId, accountId, purpose)
                    .orElseThrow(() -> conflict);
            return validateRepeatedFreeze(committed, amountFen, branchXid);
        }
        Account account = requiredAccount(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException(AccountErrorCode.ACCOUNT_UNAVAILABLE);
        }
        AccountBalance balance = requiredBalance(accountId);
        long expectedVersion = balance.getVersion();
        try {
            balance.freeze(amountFen, now);
        } catch (IllegalStateException exception) {
            if ("账户可用余额不足".equals(exception.getMessage())) {
                throw new BusinessException(AccountErrorCode.INSUFFICIENT_BALANCE);
            }
            throw exception;
        }
        if (!accountRepository.updateBalanceForActiveAccount(balance, expectedVersion)) {
            throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        }
        return record;
    }

    /** 执行 Confirm 扣除冻结金额；重复确认保持幂等。 */
    @Transactional
    public FreezeRecord confirm(String transactionId, String accountId, FreezePurpose purpose, Instant now) {
        FreezeRecord record = requiredFreezeForUpdate(transactionId, accountId, purpose);
        if (record.getStatus() == FreezeStatus.CONFIRMED) return record;
        long freezeVersion = record.getVersion();
        AccountBalance balance = requiredBalance(accountId);
        long balanceVersion = balance.getVersion();
        balance.confirm(record.getAmountFen(), now);
        record.confirm(now);
        saveState(balance, balanceVersion, record, freezeVersion);
        return record;
    }

    /** 执行 Cancel 释放冻结金额；重复释放保持幂等。 */
    @Transactional
    public FreezeRecord cancel(String transactionId, String accountId, FreezePurpose purpose, Instant now) {
        FreezeRecord record = requiredFreezeForUpdate(transactionId, accountId, purpose);
        if (record.getStatus() == FreezeStatus.RELEASED) return record;
        long freezeVersion = record.getVersion();
        AccountBalance balance = requiredBalance(accountId);
        long balanceVersion = balance.getVersion();
        balance.cancel(record.getAmountFen(), now);
        record.cancel(now);
        saveState(balance, balanceVersion, record, freezeVersion);
        return record;
    }

    private void saveState(AccountBalance balance, long balanceVersion, FreezeRecord record, long freezeVersion) {
        if (!accountRepository.updateBalance(balance, balanceVersion)
                || !freezeRecordRepository.update(record, freezeVersion)) {
            throw new BusinessException(AccountErrorCode.VERSION_CONFLICT);
        }
    }

    private Account requiredAccount(String accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private AccountBalance requiredBalance(String accountId) {
        return accountRepository.findBalance(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
    }

    private FreezeRecord requiredFreezeForUpdate(String transactionId, String accountId, FreezePurpose purpose) {
        return freezeRecordRepository.findForUpdate(transactionId, accountId, purpose)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    private FreezeRecord validateRepeatedFreeze(FreezeRecord existing, long amountFen, String branchXid) {
        if (existing.getAmountFen() != amountFen || !existing.getBranchXid().equals(branchXid)) {
            throw new BusinessException(AccountErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return existing;
    }
}
