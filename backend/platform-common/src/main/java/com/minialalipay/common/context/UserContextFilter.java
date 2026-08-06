package com.minialalipay.common.context;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.util.Set;

/**
 * 用户上下文提取过滤器。
 *
 * <p>从网关透传的 {@code X-User-Id} 和 {@code X-User-Roles} 请求头中提取用户身份信息，
 * 写入 {@link UserContextHolder}（ThreadLocal），供下游服务的业务层直接通过
 * {@link UserContextHolder#current()} 获取当前用户，无需在每个 Controller 方法上
 * 重复声明 {@code @RequestHeader("X-User-Id")}。</p>
 *
 * <h3>处理流程</h3>
 * <ol>
 *   <li>从请求头读取 {@code X-User-Id} 和 {@code X-User-Roles}</li>
 *   <li>如果存在用户标识，构建 {@link UserContext} 并写入 ThreadLocal</li>
 *   <li>执行后续过滤器链和 Controller</li>
 *   <li>在 {@code finally} 块中清理 ThreadLocal，防止线程池复用导致上下文泄漏</li>
 * </ol>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>用户身份的唯一可信来源是网关认证端口，本过滤器仅做提取和传播</li>
 *   <li>白名单路径（如登录、注册）没有 {@code X-User-Id} 头，过滤器正常放行不设置上下文</li>
 *   <li>内部接口（{@code /internal/v1/}）通常不经过网关，没有用户头，不设置上下文</li>
 * </ul>
 */
public class UserContextFilter implements Filter {

    @Override
    public void doFilter(
            ServletRequest servletRequest,
            ServletResponse servletResponse,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String userId = request.getHeader(UserContext.HEADER_USER_ID);
        String rolesHeader = request.getHeader(UserContext.HEADER_USER_ROLES);

        if (userId != null && !userId.isBlank()) {
            Set<String> roles = parseRoles(rolesHeader);
            UserContextHolder.set(new UserContext(userId, roles));
        }

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            UserContextHolder.clear();
        }
    }

    /**
     * 解析逗号分隔的角色头为角色集合。
     *
     * <p>网关通过 {@code X-User-Roles} 头传递逗号分隔的角色列表，
     * 如 {@code "USER,ADMIN"}。头为空时返回空集合。</p>
     *
     * @param rolesHeader 逗号分隔的角色字符串，可为 {@code null}
     * @return 解析后的角色集合
     */
    private Set<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Set.of();
        }
        return Set.of(rolesHeader.split(","));
    }
}
