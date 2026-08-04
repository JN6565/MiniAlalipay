/**
 * AI Agent 会话领域。
 *
 * <p>核心职责：
 * <ul>
 *   <li>管理 AgentSession 聚合根的生命周期和状态流转</li>
 *   <li>定义会话状态、消息角色和意图类型的领域枚举</li>
 *   <li>提供 AgentMessage 实体和会话聚合的不变量校验</li>
 * </ul>
 *
 * <p>本包不依赖 Spring MVC、MyBatis、Redis 或其他限界上下文的具体实现；
 * Repository 接口定义在本包，实现在 infrastructure 层。</p>
 */
package com.minialalipay.ai.domain.agent;
