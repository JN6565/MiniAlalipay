/**
 * 银行卡应用层。
 *
 * <p>职责：编排绑卡、列表、详情、设默认、解绑用例，管理事务边界，
 * 保护跨聚合不变量（默认卡至多一张、解绑默认卡递补）。
 * 允许依赖银行卡领域层、账户领域层的查询端口与平台通用技术类型；
 * 禁止依赖 Spring MVC、持久化框架或其他限界上下文。</p>
 */
package com.minialalipay.account.application.bankcard;
