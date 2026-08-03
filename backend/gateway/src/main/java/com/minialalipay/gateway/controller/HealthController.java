package com.minialalipay.gateway.controller;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.gateway.filter.RequestIdGlobalFilter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 网关健康检查与链路验证接口。
 *
 * <p>用于验证网关自身可用性及到下游服务的连通性。
 * 健康检查仅反映网关自身状态，不因下游不可用伪造 {@code UP}。</p>
 */
@RestController
public class HealthController {

    @GetMapping(value = "/actuator/healthcheck", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Map<String, String>>> healthCheck(ServerWebExchange exchange) {
        String requestId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID);
        return Mono.just(ApiResponse.success(
                Map.of("status", "UP"),
                requestId));
    }
}
