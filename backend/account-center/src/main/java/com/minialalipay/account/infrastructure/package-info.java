/**
 * 账户中心基础设施层。
 *
 * <p>职责：实现领域端口的技术适配，包括持久化、缓存和外部服务客户端。
 * 不创建业务交易主单，也不自行决定业务订单终态。</p>
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
 *   <li>业务中心的交易主单、订单或领域类型</li>
 * </ul>
 */
package com.minialalipay.account.infrastructure;
