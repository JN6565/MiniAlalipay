package com.minialalipay.ai.infrastructure.client;

/**
 * 当前请求的调用上下文持有者。
 *
 * <p>AI 服务通过网关调用下游时，需要携带原始 Bearer Token 以通过网关鉴权。
 * 当前服务基于 Servlet（非 WebFlux），使用 ThreadLocal 安全地在同一请求线程内传递上下文。</p>
 *
 * <h3>生命周期</h3>
 * <ul>
 *   <li>{@link AgentController} 在处理开始时设置 Token</li>
 *   <li>HTTP 客户端在发起下游调用时读取 Token 并注入 Authorization 头</li>
 *   <li>请求处理完成后必须调用 {@link #clear()} 防止内存泄漏</li>
 * </ul>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>Token 只在请求线程内可见，不跨线程泄漏</li>
 *   <li>不记录到日志、数据库或响应中</li>
 * </ul>
 */
public final class RequestContext {

    private static final InheritableThreadLocal<String> BEARER_TOKEN = new InheritableThreadLocal<>();

    private RequestContext() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /**
     * 设置当前请求的 Bearer Token（不含 "Bearer " 前缀）。
     *
     * @param token 原始令牌字符串
     */
    public static void setBearerToken(String token) {
        BEARER_TOKEN.set(token);
    }

    /**
     * 获取当前请求的 Bearer Token（不含 "Bearer " 前缀）。
     *
     * @return 令牌字符串，未设置时返回 null
     */
    public static String getBearerToken() {
        return BEARER_TOKEN.get();
    }

    /**
     * 获取完整的 Authorization 头值（"Bearer " + token）。
     *
     * @return Authorization 头值，未设置时返回 null
     */
    public static String getAuthorizationHeader() {
        String token = BEARER_TOKEN.get();
        return token != null ? "Bearer " + token : null;
    }

    /**
     * 清除当前线程的上下文，防止内存泄漏。
     * 必须在请求处理完成后的 finally 块中调用。
     */
    public static void clear() {
        BEARER_TOKEN.remove();
    }
}
