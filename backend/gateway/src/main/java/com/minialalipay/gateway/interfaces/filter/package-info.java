/**
 * 网关响应式过滤器。
 *
 * <p>负责请求编号、鉴权、CSRF、限流响应、安全响应头、访问日志和下游错误标准化。
 * 过滤器只处理协议和安全边界，不直接执行业务操作。</p>
 */
package com.minialalipay.gateway.interfaces.filter;
