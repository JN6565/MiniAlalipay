package com.minialalipay.ai.infrastructure.error;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.ai.infrastructure.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * AI 服务统一异常处理器。
 *
 * <p>将所有未捕获异常转换为项目统一响应格式。
 * 响应使用中文错误信息，不暴露类名、堆栈和内部细节。</p>
 */
@RestControllerAdvice
public class AiServiceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiServiceExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        String requestId = request.getHeader(RequestIdFilter.HEADER_NAME);
        log.info("请求参数不合法: requestId={}", requestId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure(CommonErrorCode.INVALID_REQUEST, requestId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUncaught(
            Exception ex, HttpServletRequest request) {
        String requestId = request.getHeader(RequestIdFilter.HEADER_NAME);
        log.error("AI 服务未预期异常: requestId={}, error={}", requestId, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR, requestId));
    }
}
