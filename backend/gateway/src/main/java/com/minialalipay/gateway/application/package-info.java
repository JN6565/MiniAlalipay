/**
 * 网关应用层。
 *
 * <p>保存网关需要的稳定端口和认证上下文，不依赖 Spring MVC、WebClient、Redis 或具体
 * 认证实现。网关没有独立业务领域模型，认证端口只表达“校验令牌并返回可信身份”的应用契约。</p>
 */
package com.minialalipay.gateway.application;
