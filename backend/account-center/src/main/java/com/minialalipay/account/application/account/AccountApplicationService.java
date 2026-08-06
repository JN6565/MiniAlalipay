package com.minialalipay.account.application.account;

import com.minialalipay.account.application.account.dto.AccountSummaryDTO;
import com.minialalipay.account.domain.account.Account;
import com.minialalipay.account.domain.account.AccountBalance;
import com.minialalipay.account.domain.account.AccountRepository;
import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.domain.ledger.LedgerAccount;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import com.minialalipay.account.domain.account.AccountErrorCode;

import java.time.Instant;

/**
 * 账户开户与本人余额查询应用服务。
 *
 * <p>开户事务同时创建账户、零余额、信用额度和信用应收，重复 registrationId 返回既有事实；
 * 查询始终回源余额表。调用方必须从可信会话取得 userId，不得采用客户端提交的账户归属字段。</p>
 */
@Service
public class AccountApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AccountApplicationService.class);

    private final AccountRepository accountRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final CreditAccountRepository creditAccountRepository;
    private final CreditReceivableRepository creditReceivableRepository;

    public AccountApplicationService(AccountRepository accountRepository,
                                     LedgerAccountRepository ledgerAccountRepository,
                                     CreditAccountRepository creditAccountRepository,
                                     CreditReceivableRepository creditReceivableRepository) {
        this.accountRepository = accountRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.creditAccountRepository = creditAccountRepository;
        this.creditReceivableRepository = creditReceivableRepository;
    }

    /**
     * 幂等创建普通用户账户。
     *
     * <p>开户事务同时创建：
     * <ol>
     *   <li>余额账户 + 余额记录（初始 0）</li>
     *   <li>账本科目</li>
     *   <li>信用账户（固定 5000 元额度）</li>
     *   <li>信用应收记录（初始 0）</li>
     * </ol>
     * </p>
     *
     * @param accountId 新账户 ID，仅首次开户使用
     * @param userId 用户 ID
     * @param registrationId 注册幂等编号
     * @param now 开户时间
     * @return 已创建或既有账户的零余额摘要
     */
    @Transactional
    public AccountSummaryDTO openAccount(String accountId, String userId, String registrationId, Instant now) {
        // 1. 创建或获取余额账户
        Account account = accountRepository.findByRegistrationId(registrationId).orElse(null);
        if (account == null) {
            account = Account.open(accountId, userId, registrationId, now);
            try {
                accountRepository.create(account, AccountBalance.zero(accountId, now));
                log.info("创建余额账户成功: accountId={}, userId={}", accountId, userId);
            } catch (DataIntegrityViolationException conflict) {
                // 唯一键是并发开户的幂等屏障；竞争失败方只能回读同一注册编号的已提交事实。
                account = accountRepository.findByRegistrationId(registrationId).orElseThrow(() -> conflict);
            }
        }
        if (!account.getUserId().equals(userId)) {
            throw new BusinessException(AccountErrorCode.IDEMPOTENCY_CONFLICT);
        }

        // 2. 创建账本科目（如果不存在）
        if (ledgerAccountRepository.findUserBalanceByUserId(userId).isEmpty()) {
            ledgerAccountRepository.create(LedgerAccount.userBalance(account.getAccountId(), userId,
                    account.getAccountId(), now));
            log.info("创建账本科目成功: userId={}", userId);
        }

        // 3. 创建信用账户（如果不存在）
        CreditAccount creditAccount = creditAccountRepository.findByUserId(userId).orElse(null);
        if (creditAccount == null) {
            String creditAccountId = generateCreditAccountId();
            creditAccount = new CreditAccount(creditAccountId, userId, now);
            creditAccountRepository.save(creditAccount);
            log.info("创建信用账户成功: creditAccountId={}, userId={}, 额度={}分",
                    creditAccountId, userId, CreditAccount.FIXED_TOTAL_LIMIT_FEN);

            // 4. 创建信用应收记录
            CreditReceivable receivable = new CreditReceivable(creditAccountId, now);
            creditReceivableRepository.save(receivable);
            log.info("创建信用应收记录成功: creditAccountId={}", creditAccountId);
        }

        // 信用应收必须有独立资产科目，禁止在信用支付时退化为付款用户余额科目。
        if (ledgerAccountRepository.findCreditReceivableByCreditAccountId(creditAccount.getCreditAccountId()).isEmpty()) {
            ledgerAccountRepository.create(LedgerAccount.creditReceivable(
                    creditAccount.getCreditAccountId(), creditAccount.getCreditAccountId(), now));
            log.info("创建信用应收账本科目成功: creditAccountId={}", creditAccount.getCreditAccountId());
        }

        return summary(account, requiredBalance(account.getAccountId()));
    }

    /**
     * 生成信用账户 ID。
     *
     * @return 26 位信用账户 ID
     */
    private String generateCreditAccountId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
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
