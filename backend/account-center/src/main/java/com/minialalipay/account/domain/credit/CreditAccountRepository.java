package com.minialalipay.account.domain.credit;

import java.util.List;
import java.util.Optional;

/**
 * 信用额度账户仓储接口。
 */
public interface CreditAccountRepository {
    Optional<CreditAccount> findByUserId(String userId);
    Optional<CreditAccount> findById(String creditAccountId);

    /**
     * 按状态查询信用账户列表。
     *
     * <p>用于到期检查任务查询所有 SUSPENDED 账户，判断是否可以恢复为 ACTIVE。</p>
     *
     * @param status 信用账户状态
     * @return 匹配状态的信用账户列表
     */
    List<CreditAccount> findByStatus(CreditAccountStatus status);

    void save(CreditAccount account);
}
