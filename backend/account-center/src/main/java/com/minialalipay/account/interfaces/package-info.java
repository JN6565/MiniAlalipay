/**
 * 账户中心接口层。
 *
 * <p>职责：REST Controller 入站适配器，负责协议转换和请求/响应序列化。
 * 仅接收和返回 OpenAPI DTO，不包含领域逻辑。</p>
 *
 * <h3>允许直接依赖</h3>
 * <ul>
 *   <li>OpenAPI 生成的 API DTO</li>
 *   <li>应用服务（application 层）</li>
 *   <li>入站协议类型</li>
 * </ul>
 *
 * <h3>禁止直接依赖</h3>
 * <ul>
 *   <li>Repository 实现、Mapper、PO</li>
 *   <li>业务中心的交易主单或订单类型</li>
 * </ul>
 */
package com.minialalipay.account.interfaces;
