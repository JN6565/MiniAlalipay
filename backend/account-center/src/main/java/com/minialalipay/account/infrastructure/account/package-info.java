/**
 * 账户与余额持久化适配器。
 *
 * <p>独占读写 account_db 的账户、余额和冻结事实，通过领域仓储接口向应用层提供能力；
 * 禁止跨服务 Schema 查询或绕过版本条件修改余额。</p>
 */
package com.minialalipay.account.infrastructure.account;
