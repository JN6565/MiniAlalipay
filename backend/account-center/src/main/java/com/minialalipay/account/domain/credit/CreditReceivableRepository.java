package com.minialalipay.account.domain.credit;

import java.util.Optional;

/**
 * 信用应收汇总仓储接口。
 */
public interface CreditReceivableRepository {
    Optional<CreditReceivable> findByCreditAccountId(String creditAccountId);
    void save(CreditReceivable receivable);
}
