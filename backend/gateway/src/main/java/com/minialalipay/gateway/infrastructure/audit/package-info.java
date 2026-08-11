/**
 * 网关审计日志包。
 *
 * <p>提供接入层安全事件的结构化审计日志输出能力。
 * 只记录网关可感知的安全事件（认证拒绝、CSRF拒绝、越权、限流触发等），
 * 业务级审计（登录失败、交易状态变更等）由对应服务自行记录。</p>
 *
 * <h3>允许依赖</h3>
 * <ul>
 *   <li>SLF4J 日志门面</li>
 *   <li>Java 标准库</li>
 * </ul>
 *
 * <h3>禁止事项</h3>
 * <ul>
 *   <li>禁止记录 Authorization 头、Cookie、CSRF Token 原文</li>
 *   <li>禁止依赖 Spring MVC、MyBatis、数据库或外部服务</li>
 *   <li>审计日志失败不得改变业务结果</li>
 * </ul>
 */
package com.minialalipay.gateway.infrastructure.audit;
