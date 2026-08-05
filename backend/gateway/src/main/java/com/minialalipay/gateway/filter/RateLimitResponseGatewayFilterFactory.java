package com.minialalipay.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 为网关限流器产生的空 429 响应补充统一错误响应体。
 *
 * <p>该过滤器必须位于 {@code RequestRateLimiter} 之前。若下游已经写入响应体，
 * 响应将正常透传，不会被此过滤器覆盖。</p>
 */
@Component
public class RateLimitResponseGatewayFilterFactory
        extends AbstractGatewayFilterFactory<RateLimitResponseGatewayFilterFactory.Config> {

    private final ObjectMapper objectMapper;

    public RateLimitResponseGatewayFilterFactory(ObjectMapper objectMapper) {
        super(Config.class);
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpResponse decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {
                @Override
                public Mono<Void> setComplete() {
                    if (getStatusCode() != HttpStatus.TOO_MANY_REQUESTS || isCommitted()) {
                        return super.setComplete();
                    }
                    return writeRateLimitResponse(exchange, this);
                }
            };
            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        };
    }

    private Mono<Void> writeRateLimitResponse(ServerWebExchange exchange, ServerHttpResponse response) {
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME);
        if (requestId == null) {
            requestId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID);
        }
        String traceId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_TRACE_ID);
        ApiResponse<Void> body = ApiResponse.failure(CommonErrorCode.RATE_LIMITED, requestId, traceId);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            response.getHeaders().set("Retry-After", String.valueOf(Duration.ofMinutes(1).toSeconds()));
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Flux.just(buffer));
        } catch (Exception exception) {
            return Mono.error(exception);
        }
    }

    /** 无配置标记类型。 */
    public static final class Config {
    }
}
