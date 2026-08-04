package com.minialalipay.account.domain.account;

import java.util.Optional;

/**
 * 账户与余额仓储端口。
 *
 * <p>开户必须在同一本地事务写入账户和零余额；余额更新必须使用调用方读取到的版本执行 CAS。</p>
 */
public interface AccountRepository {

    /** @return 注册编号对应的账户，不存在时为空 */
    Optional<Account> findByRegistrationId(String registrationId);

    /** @return 用户唯一的个人账户，不存在时为空 */
    Optional<Account> findByUserId(String userId);

    /** @return 指定账户，不存在时为空 */
    Optional<Account> findById(String accountId);

    /** @return 指定账户的余额事实，不存在时为空 */
    Optional<AccountBalance> findBalance(String accountId);

    /**
     * 原子创建账户与零余额。
     *
     * @param account 账户身份
     * @param balance 对应零余额
     */
    void create(Account account, AccountBalance balance);

    /**
     * 按预期版本更新余额。
     *
     * @return 更新成功返回 true，版本变化返回 false
     */
    boolean updateBalance(AccountBalance balance, long expectedVersion);

    /**
     * 仅当账户仍为正常状态且余额版本匹配时原子冻结余额。
     *
     * @return 两个条件均满足并更新成功时返回 true
     */
    boolean updateBalanceForActiveAccount(AccountBalance balance, long expectedVersion);
}
