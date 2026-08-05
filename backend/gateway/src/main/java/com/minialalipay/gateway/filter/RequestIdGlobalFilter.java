package com.minialalipay.gateway.filter;

import com.minialalipay.common.trace.RequestIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * 请求编号生成与透传过滤器。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>客户端已提供合法 {@code X-Request-Id} 时透传</li>
 *   <li>未提供或格式不安全时生成新的请求编号</li>
 *   <li>将 {@code requestId} 写入下游请求头、响应头和 Exchange 属性</li>
 *   <li>将 {@code traceId} 写入 Exchange 属性和 Reactor Context，供其他 Filter 和异常处理器读取</li>
 * </ul>
 *
 * <p>非法请求编号（注入字符、超长等）由 {@link RequestIdGenerator} 统一拒绝并替换，
 * 不会把攻击负载传播到下游。</p>
 */
@Component
public final class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestIdGlobalFilter.class);

    /** 请求编号 HTTP 头名称。 */
    public static final String HEADER_NAME = "X-Request-Id";

    /** 链路编号 HTTP 头名称。 */
    public static final String TRACE_HEADER_NAME = "X-Trace-Id";

    private static final java.util.regex.Pattern SAFE_TRACE_ID =
            java.util.regex.Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    /** Exchange 属性中请求编号的键（供异常处理器读取）。 */
    public static final String ATTR_REQUEST_ID = "gateway.requestId";

    /** Exchange 属性中链路编号的键（供异常处理器读取）。 */
    public static final String ATTR_TRACE_ID = "gateway.traceId";

    /** Reactor Context 中请求编号的键（供 Filter 链读取）。 */
    public static final String CONTEXT_KEY_REQUEST_ID = "gateway.requestId";

    /** Reactor Context 中链路编号的键（供 Filter 链读取）。 */
    public static final String CONTEXT_KEY_TRACE_ID = "gateway.traceId";

    private final RequestIdGenerator requestIdGenerator;

    public RequestIdGlobalFilter(RequestIdGenerator requestIdGenerator) {
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientHeader = exchange.getRequest().getHeaders().getFirst(HEADER_NAME);
        String requestId = requestIdGenerator.resolve(clientHeader);
        String clientTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER_NAME);
        String traceId = resolveTraceId(clientTraceId);

        if (clientHeader != null && !clientHeader.equals(requestId)) {
            log.warn("请求编号格式不安全，已替换: 原始值已丢弃, requestId={}", requestId);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.set(HEADER_NAME, requestId);
                    headers.set(TRACE_HEADER_NAME, traceId);
                })
                .build();

        // 写入 Exchange 属性，确保异常处理器能读取
        exchange.getAttributes().put(ATTR_REQUEST_ID, requestId);
        exchange.getAttributes().put(ATTR_TRACE_ID, traceId);

        // 设置响应头
        exchange.getResponse().getHeaders().set(HEADER_NAME, requestId);

        return chain.filter(exchange.mutate().request(request).build())
                .contextWrite(Context.of(
                        CONTEXT_KEY_REQUEST_ID, requestId,
                        CONTEXT_KEY_TRACE_ID, traceId
                ));
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.REQUEST_CONTEXT;
    }

    /**
     * 解析链路追踪编号。
     *
     * <p>客户端提供的合法 {@code X-Trace-Id} 予以信任透传；不安全格式替换为新 UUID。
     * 客户端不得伪造网关内部 Trace 头——该规则由调用方负责清理伪造头。
     *
     * <p>当前使用 UUID 作为 Trace ID，后续接入分布式追踪系统后统一为 W3C traceparent。
     *
     * @param clientTraceId 客户端提交的 Trace ID，可为 null
     * @return 安全的 Trace ID
     */
    private String resolveTraceId(String clientTraceId) {
        if (clientTraceId != null && SAFE_TRACE_ID.matcher(clientTraceId).matches()) {
            return clientTraceId;
        }
        return UUID.randomUUID().toString();
    }
}
