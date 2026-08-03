package com.minialalipay.ai.interfaces.web;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.ai.infrastructure.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 服务健康检查接口。
 */
@RestController
public class HealthController {

    @GetMapping(value = "/actuator/healthcheck", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, String>> healthCheck(HttpServletRequest request) {
        String requestId = request.getHeader(RequestIdFilter.HEADER_NAME);
        return ApiResponse.success(Map.of("service", "ai-service", "status", "UP"), requestId);
    }
}
