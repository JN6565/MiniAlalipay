package com.minialalipay.account.domain.ledger;

import java.util.Optional;

/** 账本科目仓储端口，仅允许按所有者查询和新增科目身份。 */
public interface LedgerAccountRepository {

    /** @return 用户余额负债科目，不存在时为空 */
    Optional<LedgerAccount> findUserBalanceByUserId(String userId);

    /** 新增不可变科目身份。 */
    void create(LedgerAccount account);
}
