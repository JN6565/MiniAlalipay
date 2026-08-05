/**
 * 账户查询、开户和跨服务账户解析 HTTP 接口。
 *
 * <p>{@code /api/v1/accounts/**} 只接收网关可信身份并提供本人只读查询；
 * {@code /internal/v1/accounts/**} 只供用户中心和业务中心进行幂等开户或账户解析，
 * 不注册网关路由。所有接口只返回 API DTO，不暴露余额写操作、仓储或持久化对象。</p>
 */
package com.minialalipay.account.interfaces.account;
