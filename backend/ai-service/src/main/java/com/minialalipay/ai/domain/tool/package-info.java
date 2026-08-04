/**
 * AI 工具与 MCP 协议领域。
 *
 * <p>核心职责：
 * <ul>
 *   <li>定义 ToolRiskLevel 风险分级，驱动策略网关的权限判断</li>
 *   <li>保存 ToolCallLog 实体，用于工具调用的审计和 Trace 追踪</li>
 * </ul>
 *
 * <p>工具调用日志只保存脱敏摘要和标准结果码，不保存支付密码、
 * 令牌或资金敏感响应原文。</p>
 */
package com.minialalipay.ai.domain.tool;
