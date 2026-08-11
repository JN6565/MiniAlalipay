/**
 * MiniAlalipay API 网关。
 *
 * <h3>目录职责</h3>
 * <p>作为 Web、H5、B 端和 AI/MCP 外部调用的统一入口（{@code http://localhost:8080}），
 * 负责 TLS 终止、认证转发、CSRF/CORS、限流、Trace、路由、安全响应头和协议级错误转换。
 * 网关不持久化业务数据，不编排业务流程，不修改金额、余额、额度、账本或交易状态。</p>
 *
 * <h3>主要入口</h3>
 * <ul>
 *   <li>{@link com.minialalipay.gateway.GatewayApplication} — 应用启动入口</li>
 *   <li>{@link com.minialalipay.gateway.interfaces.controller.HealthController} — 健康检查</li>
 * </ul>
 *
 * <h3>分层边界</h3>
 * <ul>
 *   <li>{@code interfaces} — HTTP Controller、全局过滤器和协议异常处理</li>
 *   <li>{@code application} — 认证端口与可信认证上下文</li>
 *   <li>{@code infrastructure} — 用户中心适配器、缓存、JWT、审计和 Spring 配置</li>
 * </ul>
 * <p>网关不持有业务领域模型，因此不建立账户、交易或 Agent 的 {@code domain} 包。</p>
 *
 * <h3>允许依赖</h3>
 * <ul>
 *   <li>{@code platform-common} 中的技术通用类型（ApiResponse、CommonErrorCode、RequestIdGenerator）</li>
 *   <li>Spring Cloud Gateway、Spring Boot Actuator、Spring Data Redis Reactive</li>
 *   <li>Nacos 服务发现</li>
 * </ul>
 *
 * <h3>禁止事项</h3>
 * <ul>
 *   <li>禁止引入 {@code spring-boot-starter-web}、Servlet Filter 或阻塞式客户端</li>
 *   <li>禁止直接访问任何业务数据库或 Redis 中的业务数据</li>
 *   <li>禁止导入其他服务的 Mapper、仓储、PO、实体或聚合根</li>
 *   <li>禁止根据 HTTP 状态码自行推断资金交易结果</li>
 *   <li>禁止为未实现操作开放路由</li>
 * </ul>
 */
package com.minialalipay.gateway;
