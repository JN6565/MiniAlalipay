package com.minialalipay.account.application.credit;

import com.minialalipay.account.application.credit.dto.CreditSummaryDTO;
import com.minialalipay.account.domain.credit.CreditAccount;
import com.minialalipay.account.domain.credit.CreditAccountRepository;
import com.minialalipay.account.domain.credit.CreditReceivable;
import com.minialalipay.account.domain.credit.CreditReceivableRepository;
import com.minialalipay.account.domain.ledger.LedgerAccount;
import com.minialalipay.account.domain.ledger.LedgerAccountRepository;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mini 花呗显式开通应用服务。
 *
 * <p>注册开户会预创建未开通信用账户，本服务只记录用户主动开通事实，并补齐信用应收与账本科目。
 * 重复调用不会重复创建额度或应收，保证开通动作可安全重试。</p>
 */
@Service
public class CreditOpeningService {
    private final CreditAccountRepository creditAccounts;
    private final CreditReceivableRepository receivables;
    private final LedgerAccountRepository ledgerAccounts;
    private final IdempotencyKeyValidator keys;
    private final Clock clock;

    /** 注入开通所需仓储和幂等键校验器。 */
    @Autowired
    public CreditOpeningService(CreditAccountRepository creditAccounts, CreditReceivableRepository receivables,
                                LedgerAccountRepository ledgerAccounts, IdempotencyKeyValidator keys) {
        this(creditAccounts, receivables, ledgerAccounts, keys, Clock.systemUTC());
    }

    CreditOpeningService(CreditAccountRepository creditAccounts, CreditReceivableRepository receivables,
                         LedgerAccountRepository ledgerAccounts, IdempotencyKeyValidator keys, Clock clock) {
        this.creditAccounts = creditAccounts;
        this.receivables = receivables;
        this.ledgerAccounts = ledgerAccounts;
        this.keys = keys;
        this.clock = clock;
    }

    /**
     * 幂等开通当前用户的 Mini 花呗。
     *
     * @param userId 可信会话用户 ID
     * @param idempotencyKey 写操作幂等键；本期无独立开通表，使用账户开通事实本身去重
     * @return 开通后的信用摘要
     */
    @Transactional
    public CreditSummaryDTO open(String userId, String idempotencyKey) {
        if (!keys.isValid(idempotencyKey)) {
            throw new com.minialalipay.common.error.BusinessException(
                    com.minialalipay.common.error.CommonErrorCode.INVALID_REQUEST);
        }
        Instant now = clock.instant();
        CreditAccount account = creditAccounts.findByUserId(userId)
                .orElseGet(() -> CreditAccount.provisionedUnopened(generateCreditAccountId(), userId, now));
        boolean alreadyOpened = account.isOpened();
        account.open(now);
        if (!alreadyOpened) {
            creditAccounts.save(account);
        }
        if (receivables.findByCreditAccountId(account.getCreditAccountId()).isEmpty()) {
            receivables.save(new CreditReceivable(account.getCreditAccountId(), now));
        }
        if (ledgerAccounts.findCreditReceivableByCreditAccountId(account.getCreditAccountId()).isEmpty()) {
            ledgerAccounts.create(LedgerAccount.creditReceivable(
                    account.getCreditAccountId(), account.getCreditAccountId(), now));
        }
        CreditReceivable receivable = receivables.findByCreditAccountId(account.getCreditAccountId())
                .orElseGet(() -> new CreditReceivable(account.getCreditAccountId(), now));
        return new CreditSummaryDTO(account.getCreditAccountId(), account.isOpened(), account.getStatus().name(),
                account.getTotalLimitFen(), account.getUsedFen(), account.getFrozenFen(), account.getAvailableFen(),
                receivable.getUnbilledFen(), receivable.getBilledFen(), receivable.getOverdueFen());
    }

    /** 生成 26 位演示信用账户 ID。 */
    private String generateCreditAccountId() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 26).toUpperCase();
    }
}
