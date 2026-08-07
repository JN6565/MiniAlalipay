package com.minialalipay.account.interfaces.filter;

import io.seata.core.context.RootContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 将业务中心 HTTP 调用携带的 Seata XID 绑定到当前线程。
 *
 * <p>Seata 的 Confirm/Cancel 由 TC 通过 Seata RPC 回调，不经过本过滤器；过滤器只处理内部
 * Try 入口，并在请求结束时解除绑定，防止线程池复用造成跨请求事务污染。</p>
 */
@Component
public class SeataXidPropagationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String xid = request.getHeader(RootContext.KEY_XID);
        boolean bound = xid != null && xid.length() <= 128 && !xid.isBlank() && !RootContext.inGlobalTransaction();
        if (bound) {
            RootContext.bind(xid);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (bound) {
                RootContext.unbind();
            }
        }
    }
}
