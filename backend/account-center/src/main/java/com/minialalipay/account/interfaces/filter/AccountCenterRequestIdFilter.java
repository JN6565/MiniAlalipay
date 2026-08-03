package com.minialalipay.account.interfaces.filter;

import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 在账户中心请求入口建立请求编号上下文，并保证响应始终返回相同编号。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountCenterRequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String REQUEST_ATTRIBUTE = "minialalipay.requestId";
    public static final String MDC_KEY = "requestId";

    private final RequestIdGenerator requestIdGenerator;

    public AccountCenterRequestIdFilter(RequestIdGenerator requestIdGenerator) {
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = requestIdGenerator.resolve(request.getHeader(HEADER_NAME));
        String previousRequestId = MDC.get(MDC_KEY);
        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            restoreMdc(previousRequestId);
        }
    }

    private void restoreMdc(String previousRequestId) {
        if (previousRequestId == null) {
            MDC.remove(MDC_KEY);
        } else {
            MDC.put(MDC_KEY, previousRequestId);
        }
    }
}
