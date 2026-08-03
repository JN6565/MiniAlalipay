/** 负责账户、账本、信用和 TCC 分支等用例编排。 */
/**
 * 账户中心应用层。
 *
 * <p>职责：编排账户、余额、TCC、账本、信用、账单和还款等资金核心用例。
 * 本层是唯一可以修改余额、信用额度和账本数据的入口。</p>
 *
 * <h3>允许直接依赖</h3>
 * <ul>
 *   <li>本上下文领域类型（domain.account、domain.ledger、domain.tcc、domain.credit）</li>
 *   <li>领域端口（application/port）</li>
 *   <li>应用命令和查询对象</li>
 *   <li>事务抽象</li>
 * </ul>
 *
 * <h3>禁止直接依赖</h3>
 * <ul>
 *   <li>HTTP Controller</li>
 *   <li>MyBatis Mapper 和 PO</li>
 *   <li>Redis 等具体基础设施实现</li>
 *   <li>业务中心的交易主单或订单类型</li>
 * </ul>
 */
package com.minialalipay.account.application;
