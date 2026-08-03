/**
 * 业务中心应用层。
 *
 * <p>职责：编排转账、扫码支付、C2C 收款、风控、工单和监控等用例。
 * 所有资金执行统一调用交易、确认和 TCC 应用接口，不自行影响资金事实。</p>
 *
 * <h3>允许直接依赖</h3>
 * <ul>
 *   <li>本上下文领域类型（domain.transaction、domain.transfer、domain.qrpay 等）</li>
 *   <li>领域端口（application/port）</li>
 *   <li>应用命令和查询对象</li>
 *   <li>事务抽象</li>
 * </ul>
 *
 * <h3>禁止直接依赖</h3>
 * <ul>
 *   <li>HTTP Controller</li>
 *   <li>MyBatis Mapper 和 PO</li>
 *   <li>账户中心的余额/账本持久化类型</li>
 *   <li>其他服务的领域类型</li>
 * </ul>
 */
package com.minialalipay.business.application;
