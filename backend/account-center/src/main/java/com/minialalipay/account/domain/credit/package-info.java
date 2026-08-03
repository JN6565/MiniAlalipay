/**
 * 信用子域领域层。包含 Mini 花呗额度账户、冻结记录、信用应收和信用消费明细等聚合根和值对象。
 *
 * <p>该包不得依赖 Spring MVC、MyBatis、Feign、Redis、Controller 或其他限界上下文的领域包。
 * 信用子域不得绕过账户和账本应用入口直接修改 account_balance、ledger_voucher 或 ledger_entry 表。</p>
 */
package com.minialalipay.account.domain.credit;
