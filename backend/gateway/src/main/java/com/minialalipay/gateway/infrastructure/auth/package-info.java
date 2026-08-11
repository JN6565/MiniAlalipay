/**
 * 网关认证基础设施适配器。
 *
 * <p>本包实现应用层认证端口，提供用户中心回源适配器、
 * 本地演示桩、短时缓存装饰器和 JWT 技术服务。认证结果只能来自服务端可信配置
 * 或用户中心接口，不能使用客户端提交的身份头。</p>
 *
 * <h3>边界</h3>
 * <ul>
 *   <li>允许依赖 WebClient、Redis 和 Spring 配置能力。</li>
 *   <li>禁止访问业务数据库，禁止导入其他服务的领域对象。</li>
 *   <li>适配器只返回应用层认证上下文，不负责交易、账户或 Agent 业务。</li>
 * </ul>
 */
package com.minialalipay.gateway.infrastructure.auth;
