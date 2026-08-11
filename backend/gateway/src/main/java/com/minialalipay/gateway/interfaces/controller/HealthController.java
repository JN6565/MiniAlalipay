package com.minialalipay.gateway.interfaces.controller;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.gateway.interfaces.filter.RequestIdGlobalFilter;
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

    /**
     * 返回网关自身的可用状态和当前请求编号。
     *
     * <p>该接口无需业务用户权限，不访问下游业务服务，不修改数据；网关启动并能处理请求时
     * 返回 {@code UP}。请求编号由 {@link RequestIdGlobalFilter} 统一生成或透传。</p>
     *
     * @param exchange 当前响应式 HTTP 请求上下文
     * @return 包含 {@code status=UP} 和请求编号的统一响应
     */
    @GetMapping(value = "/actuator/healthcheck", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ApiResponse<Map<String, String>>> healthCheck(ServerWebExchange exchange) {
        String requestId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID);
        return Mono.just(ApiResponse.success(
                Map.of("status", "UP"),
                requestId));
    }
}
