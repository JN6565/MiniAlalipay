package com.minialalipay.account.interfaces.error;

import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.error.MappedError;
import com.minialalipay.common.trace.RequestIdGenerator;
import com.minialalipay.account.interfaces.filter.AccountCenterRequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 将账户中心接口异常转换为统一中文响应，禁止向调用方暴露内部异常消息。
 */
@RestControllerAdvice
public class AccountCenterExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AccountCenterExceptionHandler.class);

    private final CommonExceptionMapper exceptionMapper;
    private final RequestIdGenerator requestIdGenerator;

    public AccountCenterExceptionHandler(
            CommonExceptionMapper exceptionMapper,
            RequestIdGenerator requestIdGenerator
    ) {
        this.exceptionMapper = exceptionMapper;
        this.requestIdGenerator = requestIdGenerator;
    }

    /**
     * 转换已登记错误码的业务异常。
     *
     * @param exception 业务异常
     * @param request 当前 HTTP 请求
     * @return 统一错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        return toResponse(exception, request);
    }

    /**
     * 将不存在的 HTTP 资源转换为统一的 404 响应。
     *
     * @param exception 资源不存在异常
     * @param request 当前 HTTP 请求
     * @return 统一资源不存在响应
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFoundException(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return toResponse(new BusinessException(CommonErrorCode.NOT_FOUND), request);
    }

    /**
     * 将参数校验、类型转换和 JSON 解析错误转换为统一的 400 响应。
     *
     * @param exception 协议层请求参数异常
     * @param request 当前 HTTP 请求
     * @return 统一请求不合法响应
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
            Exception exception,
            HttpServletRequest request
    ) {
        return toResponse(new BusinessException(CommonErrorCode.INVALID_REQUEST), request);
    }

    /** 将不支持的 HTTP 方法转换为统一的 405 响应。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return toResponse(new BusinessException(CommonErrorCode.METHOD_NOT_ALLOWED), request);
    }

    /** 将无法协商的响应格式转换为统一的 406 响应。 */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<?>> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException exception,
            HttpServletRequest request
    ) {
        return toResponse(new BusinessException(CommonErrorCode.NOT_ACCEPTABLE), request);
    }

    /** 将不支持的请求媒体类型转换为统一的 415 响应。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return toResponse(new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE), request);
    }

    /**
     * 隐藏未分类异常的消息、堆栈和内部实现细节。
     *
     * @param exception 未分类异常
     * @param request 当前 HTTP 请求
     * @return 统一内部错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request
    ) {
        String requestId = resolveRequestId(request);
        LOGGER.error(
                "账户中心发生未处理异常，请求编号：{}，链路编号：{}，异常类型：{}",
                requestId,
                MDC.get("traceId"),
                exception.getClass().getName(),
                exception
        );
        return toResponse(exception, request);
    }

    private ResponseEntity<ApiResponse<?>> toResponse(Throwable exception, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        MappedError mappedError = exceptionMapper.map(exception, requestId, MDC.get("traceId"));
        return ResponseEntity.status(mappedError.httpStatus())
                .body(mappedError.body());
    }

    private String resolveRequestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(AccountCenterRequestIdFilter.REQUEST_ATTRIBUTE);
        return requestId instanceof String value
                ? value
                : requestIdGenerator.resolve(request.getHeader(AccountCenterRequestIdFilter.HEADER_NAME));
    }
}
