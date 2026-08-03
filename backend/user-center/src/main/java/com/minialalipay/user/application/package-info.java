/**
 * 用户中心应用层。
 *
 * <p>职责：编排用户注册、登录、会话管理、支付密码校验、联系人管理等用例。</p>
 *
 * <h3>允许直接依赖</h3>
 * <ul>
 *   <li>本上下文领域类型（domain.user、domain.identity）</li>
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
 *   <li>其他限界上下文的领域类型</li>
 * </ul>
 */
package com.minialalipay.user.application;
