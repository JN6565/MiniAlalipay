package com.minialalipay.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.gateway.filter.RequestIdGlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public final class GatewayExceptionHandler implements WebExceptionHandler, Ordered {

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }
        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME);
        ApiResponse<Void> body = ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR, requestId);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationFailure) {
            bytes = "{\"code\":\"COMMON_INTERNAL_ERROR\",\"message\":\"Internal server error\"}"
                    .getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
