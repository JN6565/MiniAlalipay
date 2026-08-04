package com.minialalipay.account.application.account;

import com.minialalipay.account.application.account.dto.AccountSummaryDTO;
import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.ledger.LedgerAccount;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import com.minialalipay.account.domain.account.AccountErrorCode;

import java.time.Instant;

/**
 * 账户开户与本人余额查询应用服务。
 *
 * <p>开户事务同时创建账户和零余额，重复 registrationId 返回既有事实；查询始终回源余额表。
 * 调用方必须从可信会话取得 userId，不得采用客户端提交的账户归属字段。</p>
 */
@Service
public class AccountApplicationService {

    private final AccountRepository accountRepository;
    private final LedgerAccountRepository ledgerAccountRepository;

    public AccountApplicationService(AccountRepository accountRepository,
                                     LedgerAccountRepository ledgerAccountRepository) {
        this.accountRepository = accountRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
    }

    /**
     * 幂等创建普通用户账户。
     *
     * @param accountId 新账户 ID，仅首次开户使用
     * @param userId 用户 ID
     * @param registrationId 注册幂等编号
     * @param now 开户时间
     * @return 已创建或既有账户的零余额摘要
     */
    @Transactional
    public AccountSummaryDTO openAccount(String accountId, String userId, String registrationId, Instant now) {
        Account account = accountRepository.findByRegistrationId(registrationId).orElse(null);
        if (account == null) {
            account = Account.open(accountId, userId, registrationId, now);
            try {
                accountRepository.create(account, AccountBalance.zero(accountId, now));
            } catch (DataIntegrityViolationException conflict) {
                // 唯一键是并发开户的幂等屏障；竞争失败方只能回读同一注册编号的已提交事实。
                // 若冲突来自用户唯一键等其他约束，则保留原异常，不能误报为幂等成功。
                account = accountRepository.findByRegistrationId(registrationId).orElseThrow(() -> conflict);
            }
        }
        if (!account.getUserId().equals(userId)) {
            throw new BusinessException(AccountErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (ledgerAccountRepository.findUserBalanceByUserId(userId).isEmpty()) {
            ledgerAccountRepository.create(LedgerAccount.userBalance(account.getAccountId(), userId,
                    account.getAccountId(), now));
        }
        return summary(account, requiredBalance(account.getAccountId()));
    }

    /**
     * 查询当前登录用户的账户和实时余额。
     *
     * @param userId 可信会话用户 ID
     * @return 本人账户摘要
     * @throws BusinessException 账户不存在时返回通用不可感知资源不存在错误
     */
    @Transactional(readOnly = true)
    public AccountSummaryDTO getMyAccount(String userId) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        return summary(account, requiredBalance(account.getAccountId()));
    }

    private AccountBalance requiredBalance(String accountId) {
        return accountRepository.findBalance(accountId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.INTERNAL_ERROR));
    }

    private AccountSummaryDTO summary(Account account, AccountBalance balance) {
        return new AccountSummaryDTO(account.getAccountId(), account.getAccountType().name(), account.getCurrency(),
                account.getStatus().name(), balance.getAvailableFen(), balance.getFrozenFen(),
                balance.getTotalFen(), balance.getVersion());
    }
}
