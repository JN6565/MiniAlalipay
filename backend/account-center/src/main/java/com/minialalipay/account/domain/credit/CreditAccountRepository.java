package com.minialalipay.account.domain.credit;

import java.util.Optional;

/**
 * 信用额度账户仓储接口。
 */
public interface CreditAccountRepository {
    Optional<CreditAccount> findByUserId(String userId);
    Optional<CreditAccount> findById(String creditAccountId);
    void save(CreditAccount account);
}
