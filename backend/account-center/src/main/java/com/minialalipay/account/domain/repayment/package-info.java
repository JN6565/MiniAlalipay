/**
 * 还款子域领域层。包含信用还款记录、还款分配计划、还款分配明细和还款草稿等聚合根和值对象。
 *
 * <p>该包不得依赖 Spring MVC、MyBatis、Feign、Redis、Controller 或其他限界上下文的领域包。
 * 还款分配在 Try 阶段固化，Confirm 阶段不得重新计算。</p>
 */
package com.minialalipay.account.domain.repayment;
