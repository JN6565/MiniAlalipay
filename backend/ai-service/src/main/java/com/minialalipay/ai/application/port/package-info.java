/**
 * AI 服务应用层端口（Port）。
 *
 * <p>定义应用层所需的外部系统接口契约，由 infrastructure 层提供具体实现。
 * 遵循六边形架构的端口-适配器模式：应用层只依赖端口接口，不依赖具体技术实现。</p>
 *
 * <h3>端口清单</h3>
 * <ul>
 *   <li>{@code LanguageModelPort}：语言模型调用契约</li>
 *   <li>{@code ChatMessage / ChatResponse}：端口级数据结构</li>
 *   <li>{@code StreamCallback}：SSE 流式回调契约</li>
 *   <li>{@code SseEvent}：SSE 事件类型与载荷定义</li>
 * </ul>
 */
package com.minialalipay.ai.application.port;
