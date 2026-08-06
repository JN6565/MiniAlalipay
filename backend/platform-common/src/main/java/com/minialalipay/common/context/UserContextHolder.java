package com.minialalipay.common.context;

import java.util.Set;

/**
 * 基于 ThreadLocal 的用户上下文持有器。
 *
 * <p>由 {@code UserContextFilter} 在请求入口设置，请求结束后自动清理。
 * 业务层通过 {@link #current()} 获取当前用户上下文，避免在方法签名中
 * 反复传递用户参数。</p>
 *
 * <p>使用约束：</p>
 * <ul>
 *   <li>仅在 Servlet 线程内有效，异步线程需手动传播</li>
 *   <li>请求结束后必须调用 {@link #clear()} 防止线程复用导致的数据泄漏</li>
 * </ul>
 */
public final class UserContextHolder {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private UserContextHolder() {
        throw new UnsupportedOperationException("工具类不可实例化");
    }

    /**
     * 设置当前线程的用户上下文。
     *
     * @param context 网关认证后透传的用户上下文
     */
    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    /**
     * 获取当前线程的用户上下文。
     *
     * @return 当前用户上下文；未设置时返回 {@code null}
     */
    public static UserContext current() {
        return HOLDER.get();
    }

    /**
     * 获取当前认证主体标识。
     *
     * @return 用户 ID；未认证时返回 {@code null}
     */
    public static String principalId() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.principalId() : null;
    }

    /**
     * 获取当前用户角色集合。
     *
     * @return 角色集合；未认证时返回空集合
     */
    public static Set<String> roles() {
        UserContext ctx = HOLDER.get();
        return ctx != null ? ctx.roles() : Set.of();
    }

    /**
     * 清理当前线程的用户上下文。
     *
     * <p>必须在请求结束时调用，防止线程池复用导致上下文泄漏。
     * 由 {@code UserContextFilter} 的 {@code finally} 块负责调用。</p>
     */
    public static void clear() {
        HOLDER.remove();
    }
}
