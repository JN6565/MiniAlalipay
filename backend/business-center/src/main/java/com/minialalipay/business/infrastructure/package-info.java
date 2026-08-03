/**
 * 业务中心基础设施层。
 *
 * <p>职责：实现领域端口的技术适配，包括持久化、缓存、消息和外部服务客户端。
 * 对账户中心的调用只能通过公开 API，不得直连其数据库或共享持久化类型。</p>
 *
 * <h3>允许直接依赖</h3>
 * <ul>
 *   <li>领域端口（application/port 中定义的接口）</li>
 *   <li>PO、Mapper 和数据库访问框架</li>
 *   <li>外部服务 HTTP 客户端</li>
 * </ul>
 *
 * <h3>禁止直接依赖</h3>
 * <ul>
 *   <li>Controller</li>
 *   <li>应用服务</li>
 *   <li>账户中心的 Mapper、PO、实体或聚合根</li>
 * </ul>
 */
package com.minialalipay.business.infrastructure;
