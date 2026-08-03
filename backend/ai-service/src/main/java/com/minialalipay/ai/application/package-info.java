/**
 * AI 服务应用层。
 *
 * <p>职责：编排 Agent 会话、消息处理、工具调用和结果解释。</p>
 *
 * <h3>允许直接依赖</h3>
 * <ul>
 *   <li>本上下文领域类型（domain.agent、domain.tool、domain.memory）</li>
 *   <li>领域端口（application/port）</li>
 *   <li>应用命令和查询对象</li>
 *   <li>事务抽象</li>
 * </ul>
 *
 * <h3>禁止直接依赖</h3>
 * <ul>
 *   <li>HTTP Controller 和 MCP 适配器</li>
 *   <li>MyBatis Mapper 和 PO</li>
 *   <li>Redis、LLM 客户端等具体基础设施实现</li>
 *   <li>其他限界上下文的领域类型</li>
 * </ul>
 */
package com.minialalipay.ai.application;
