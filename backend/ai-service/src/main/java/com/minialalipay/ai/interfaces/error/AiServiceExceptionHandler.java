package com.minialalipay.ai.interfaces.error;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.error.MappedError;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.ai.interfaces.filter.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * AI 服务统一异常处理器。
 *
 * <p>将所有接口异常转换为项目统一中文响应格式，
 * 禁止向调用方暴露内部异常消息、类名、堆栈或内部地址。</p>
 */
@RestControllerAdvice
public class AiServiceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiServiceExceptionHandler.class);

    private final CommonExceptionMapper exceptionMapper;
    private final RequestIdGenerator requestIdGenerator;

    public AiServiceExceptionHandler(
            CommonExceptionMapper exceptionMapper,
            RequestIdGenerator requestIdGenerator
    ) {
        this.exceptionMapper = exceptionMapper;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 转换已登记错误码的业务异常（含 AI 领域错误码）。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(
            BusinessException exception, HttpServletRequest request) {
        return toResponse(exception, request);
    }

    /**
     * 将参数校验、类型转换和 JSON 解析错误转换为统一的 400 响应。
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ServletRequestBindingException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<?>> handleInvalidRequest(
            Exception exception, HttpServletRequest request) {
        return toResponse(new BusinessException(CommonErrorCode.INVALID_REQUEST), request);
    }

    /** 不存在的 HTTP 资源 → 404 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFound(
            NoResourceFoundException exception, HttpServletRequest request) {
        return toResponse(new BusinessException(CommonErrorCode.NOT_FOUND), request);
    }

    /** 不支持的 HTTP 方法 → 405 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return toResponse(new BusinessException(CommonErrorCode.METHOD_NOT_ALLOWED), request);
    }

    /** 无法协商的响应格式 → 406 */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException exception, HttpServletRequest request) {
        return toResponse(new BusinessException(CommonErrorCode.NOT_ACCEPTABLE), request);
    }

    /** 不支持的请求媒体类型 → 415 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return toResponse(new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE), request);
    }

    /**
     * 域内非法参数（如 AgentSession 状态转换失败）→ 不泄露堆栈。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        log.info("请求参数或领域状态不合法: requestId={}", resolveRequestId(request));
        return toResponse(new BusinessException(CommonErrorCode.INVALID_REQUEST), request);
    }

    /**
     * 隐藏未分类异常的消息、堆栈和内部实现细节。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        log.error("AI 服务未预期异常: requestId={}, traceId={}, errorType={}",
                requestId, MDC.get("traceId"), exception.getClass().getName());
        return toResponse(exception, request);
    }

    private ResponseEntity<ApiResponse<?>> toResponse(Throwable exception, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        MappedError mappedError = exceptionMapper.map(exception, requestId, MDC.get("traceId"));
        return ResponseEntity.status(mappedError.httpStatus())
                .body(mappedError.body());
    }

    private String resolveRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        return requestId instanceof String value
                ? value
                : requestIdGenerator.resolve(request.getHeader(RequestIdFilter.HEADER_NAME));
    }
}
