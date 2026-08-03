package com.minialalipay.account.domain.credit;

import java.util.Optional;

/**
 * 信用额度冻结记录仓储接口。
 */
public interface CreditFreezeRepository {
    Optional<CreditFreeze> findByTransactionIdAndAccountId(String transactionId, String creditAccountId);
    void save(CreditFreeze freeze);
}
