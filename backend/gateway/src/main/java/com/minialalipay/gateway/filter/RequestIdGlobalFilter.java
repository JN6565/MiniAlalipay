package com.minialalipay.gateway.filter;

import com.minialalipay.common.trace.RequestIdGenerator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public final class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER_NAME = "X-Request-Id";

    private final RequestIdGenerator requestIdGenerator;

    public RequestIdGlobalFilter(RequestIdGenerator requestIdGenerator) {
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestId = requestIdGenerator.resolve(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_NAME, requestId))
                .build();
        exchange.getResponse().getHeaders().set(HEADER_NAME, requestId);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
