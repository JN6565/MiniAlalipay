/**
 * 用户中心基础设施层。
 *
 * <p>职责：实现领域端口的技术适配，包括持久化、缓存和外部服务客户端。</p>
 *
 * <h3>允许直接依赖</h3>
 * <ul>
 *   <li>领域端口（application/port 中定义的接口）</li>
 *   <li>PO、Mapper 和数据库访问框架</li>
 *   <li>外部系统客户端</li>
 * </ul>
 *
 * <h3>禁止直接依赖</h3>
 * <ul>
 *   <li>Controller</li>
 *   <li>应用服务</li>
 *   <li>其他限界上下文的领域包</li>
 * </ul>
 */
package com.minialalipay.user.infrastructure;
