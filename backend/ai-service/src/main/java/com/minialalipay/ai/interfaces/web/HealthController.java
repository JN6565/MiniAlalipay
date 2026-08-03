package com.minialalipay.ai.interfaces.web;

import com.minialalipay.common.api.ApiResponse;
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

    private static final String REQUEST_ID_ATTRIBUTE = "minialalipay.requestId";

    @GetMapping(value = "/actuator/healthcheck", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, String>> healthCheck(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(REQUEST_ID_ATTRIBUTE);
        return ApiResponse.success(Map.of("service", "ai-service", "status", "UP"), requestId);
    }
}
