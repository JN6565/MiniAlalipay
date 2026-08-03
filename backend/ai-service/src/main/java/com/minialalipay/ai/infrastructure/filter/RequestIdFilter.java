package com.minialalipay.ai.infrastructure.filter;

import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * AI 服务请求编号过滤器。
 *
 * <p>为每个 HTTP 请求生成或透传 {@code X-Request-Id}，
 * 并将请求编号写入响应头和日志上下文。</p>
 */
@Component
public final class RequestIdFilter implements Filter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String REQUEST_ATTRIBUTE = "minialalipay.requestId";
    public static final String MDC_KEY = "requestId";

    private final RequestIdGenerator requestIdGenerator;

    public RequestIdFilter(RequestIdGenerator requestIdGenerator) {
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String clientHeader = httpRequest.getHeader(HEADER_NAME);
        String requestId = requestIdGenerator.resolve(clientHeader);
        String previousRequestId = MDC.get(MDC_KEY);

        if (clientHeader != null && !clientHeader.equals(requestId)) {
            log.warn("请求编号格式不安全，已替换: requestId={}", requestId);
        }

        httpRequest.setAttribute(REQUEST_ATTRIBUTE, requestId);
        httpResponse.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previousRequestId == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previousRequestId);
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
