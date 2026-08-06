package com.minialalipay.gateway.filter;

/**
 * 网关全局 Filter 的执行顺序常量。
 *
 * <p>数值越小越先执行。Filter 之间的依赖关系通过命名常量表达，
 * 禁止在多个类中散落 {@code HIGHEST_PRECEDENCE + n} 而无整体顺序测试。</p>
 *
 * <h3>执行顺序</h3>
 * <ol>
 *   <li>{@link #REQUEST_CONTEXT} — 请求编号生成与透传，必须在日志和异常记录之前</li>
 *   <li>{@link #SECURITY_HEADERS} — 安全响应头，在鉴权之前写入</li>
 *   <li>{@link #AUTHENTICATION} — 身份认证</li>
 *   <li>{@link #CSRF} — CSRF Token 校验，在鉴权之后、限流之前</li>
 *   <li>{@link #RATE_LIMITER} — 限流，在鉴权之后、业务路由之前执行</li>
 *   <li>{@link #LOGGING} — 访问日志，在所有业务处理之后记录</li>
 * </ol>
 */
public final class GatewayFilterOrders {

    private GatewayFilterOrders() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /** 请求编号：生成 X-Request-Id 并注入 Reactor Context。 */
    public static final int REQUEST_CONTEXT = 0;

    /** 安全响应头：为所有响应添加安全相关 HTTP 头。 */
    public static final int SECURITY_HEADERS = 10;

    /** 身份认证：提取会话令牌并校验身份，注入 principalId 和 roles。 */
    public static final int AUTHENTICATION = 20;

    /** CSRF Token 校验：对 Cookie 会话写请求校验防跨站请求伪造令牌。 */
    public static final int CSRF = 25;

    /** 限流：按用户、IP 和业务动作执行速率限制。 */
    public static final int RATE_LIMITER = 30;

    /** 访问日志：记录脱敏后的请求摘要、结果码和耗时。 */
    public static final int LOGGING = 100;
}
