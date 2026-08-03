package com.minialalipay.account.interfaces;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.error.MappedError;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 账户中心全局异常处理。
 *
 * <p>将业务异常和校验异常统一转换为 {@link ApiResponse} 格式响应，
 * 不得暴露堆栈或敏感信息。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final CommonExceptionMapper exceptionMapper;
    private final RequestIdGenerator requestIdGenerator;

    /**
     * 注入异常映射器和请求编号生成器。
     */
    public GlobalExceptionHandler(CommonExceptionMapper exceptionMapper, RequestIdGenerator requestIdGenerator) {
        this.exceptionMapper = exceptionMapper;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 处理业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(
            BusinessException ex, HttpServletRequest request
    ) {
        String requestId = requestIdGenerator.resolve(request.getHeader("X-Request-Id"));
        String traceId = request.getHeader("X-Trace-Id");
        MappedError mapped = exceptionMapper.map(ex, requestId, traceId);
        return ResponseEntity.status(mapped.httpStatus()).body(mapped.body());
    }

    /**
     * 处理参数校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request
    ) {
        String requestId = requestIdGenerator.resolve(request.getHeader("X-Request-Id"));
        String traceId = request.getHeader("X-Trace-Id");
        ApiResponse<?> body = ApiResponse.failure(CommonErrorCode.INVALID_REQUEST, requestId, traceId);
        return ResponseEntity.status(400).body(body);
    }

    /**
     * 处理未捕获异常。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleException(
            Exception ex, HttpServletRequest request
    ) {
        String requestId = requestIdGenerator.resolve(request.getHeader("X-Request-Id"));
        String traceId = request.getHeader("X-Trace-Id");
        MappedError mapped = exceptionMapper.map(ex, requestId, traceId);
        return ResponseEntity.status(mapped.httpStatus()).body(mapped.body());
    }
}
