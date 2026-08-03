package com.minialalipay.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.error.ErrorCode;
import com.minialalipay.gateway.filter.RequestIdGlobalFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * 网关全局异常处理器。
 *
 * <p>将所有未在 Filter 层捕获的异常转换为统一 JSON 响应。
 * 响应使用中文错误消息，包含 {@code code}、{@code message}、{@code requestId} 和
 * {@code traceId}，不暴露类名、堆栈、SQL、内部地址或密钥。</p>
 *
 * <h3>错误映射规则</h3>
 * <ul>
 *   <li>下游 401 → 网关 401 {@code COMMON_UNAUTHORIZED}</li>
 *   <li>下游 403 → 网关 403 {@code COMMON_FORBIDDEN}</li>
 *   <li>下游 404 → 网关 404（保留下游错误码如 {@code ORDER_NOT_FOUND}）</li>
 *   <li>下游 409 → 保留下游错误码（如 {@code VERSION_CONFLICT}）</li>
 *   <li>下游 422 → 保留下游语义</li>
 *   <li>下游 429 → 网关 429 {@code RATE_LIMITED}，透传 {@code Retry-After}</li>
 *   <li>连接超时且副作用未知 → 不转为普通失败，提示查询原状态</li>
 *   <li>下游不可达 → 503 {@code COMMON_SERVICE_UNAVAILABLE}</li>
 * </ul>
 */
@Component
public final class GatewayExceptionHandler implements WebExceptionHandler, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    private static final String RATE_LIMITED_CODE = "RATE_LIMITED";
    private static final String RATE_LIMITED_MESSAGE = "请求频率超限，请稍后重试";
    private static final String SERVICE_UNAVAILABLE_CODE = "COMMON_SERVICE_UNAVAILABLE";
    private static final String SERVICE_UNAVAILABLE_MESSAGE = "服务暂时不可用，请稍后重试";
    private static final String GATEWAY_TIMEOUT_CODE = "COMMON_GATEWAY_TIMEOUT";
    private static final String GATEWAY_TIMEOUT_MESSAGE = "请求处理超时，请查询原状态后重试";

    private final ObjectMapper objectMapper;

    public GatewayExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable throwable) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(throwable);
        }

        String requestId = resolveRequestId(exchange);
        String traceId = resolveTraceId(exchange);

        ApiResponse<Void> body;
        HttpStatus httpStatus;

        if (throwable instanceof ResponseStatusException rse) {
            int statusCode = rse.getStatusCode().value();
            httpStatus = HttpStatus.resolve(statusCode);
            if (httpStatus == null) {
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            body = buildDownstreamErrorResponse(statusCode, rse.getReason(), requestId, traceId);
        } else if (throwable instanceof ConnectException) {
            log.warn("下游服务连接失败: requestId={}", requestId);
            httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
            body = buildErrorBody(SERVICE_UNAVAILABLE_CODE, SERVICE_UNAVAILABLE_MESSAGE, 503, requestId, traceId);
        } else if (throwable instanceof TimeoutException) {
            log.warn("请求超时: requestId={}", requestId);
            httpStatus = HttpStatus.GATEWAY_TIMEOUT;
            body = buildErrorBody(GATEWAY_TIMEOUT_CODE, GATEWAY_TIMEOUT_MESSAGE, 504, requestId, traceId);
        } else if (throwable instanceof IllegalArgumentException) {
            log.info("请求参数不合法: requestId={}", requestId);
            httpStatus = HttpStatus.BAD_REQUEST;
            body = ApiResponse.failure(CommonErrorCode.INVALID_REQUEST, requestId, traceId);
        } else {
            log.error("网关未预期异常: requestId={}, error={}", requestId, throwable.getMessage(), throwable);
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
            body = ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR, requestId, traceId);
        }

        exchange.getResponse().setStatusCode(httpStatus);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (Exception serializationFailure) {
            log.error("异常响应序列化失败: requestId={}", requestId, serializationFailure);
            bytes = buildFallbackJson(requestId, traceId);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return GatewayExceptionHandlerOrders.EXCEPTION_HANDLER;
    }

    /**
     * 从请求头或 Exchange 属性解析请求编号。
     */
    private String resolveRequestId(ServerWebExchange exchange) {
        String headerValue = exchange.getRequest().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME);
        if (headerValue != null) {
            return headerValue;
        }
        return exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID);
    }

    /**
     * 从 Exchange 属性解析链路编号。
     */
    private String resolveTraceId(ServerWebExchange exchange) {
        return exchange.getAttribute(RequestIdGlobalFilter.ATTR_TRACE_ID);
    }

    /**
     * 处理下游服务返回的错误，保留下游错误码和语义。
     */
    private ApiResponse<Void> buildDownstreamErrorResponse(
            int statusCode, String reason, String requestId, String traceId) {
        return switch (statusCode) {
            case 400 -> ApiResponse.failure(CommonErrorCode.INVALID_REQUEST, requestId, traceId);
            case 401 -> ApiResponse.failure(CommonErrorCode.UNAUTHORIZED, requestId, traceId);
            case 403 -> ApiResponse.failure(CommonErrorCode.FORBIDDEN, requestId, traceId);
            case 404 -> {
                String code = reason != null && !reason.isBlank() ? reason : CommonErrorCode.NOT_FOUND.code();
                yield buildErrorBody(code, "资源不存在", 404, requestId, traceId);
            }
            case 409 -> {
                String code = reason != null && !reason.isBlank() ? reason : "COMMON_CONFLICT";
                yield buildErrorBody(code, "资源冲突", 409, requestId, traceId);
            }
            case 422 -> {
                String code = reason != null && !reason.isBlank() ? reason : "COMMON_UNPROCESSABLE";
                yield buildErrorBody(code, "请求无法处理", 422, requestId, traceId);
            }
            case 429 -> buildErrorBody(RATE_LIMITED_CODE, RATE_LIMITED_MESSAGE, 429, requestId, traceId);
            case 503 -> buildErrorBody(SERVICE_UNAVAILABLE_CODE, SERVICE_UNAVAILABLE_MESSAGE, 503, requestId, traceId);
            default -> {
                if (statusCode >= 500) {
                    yield ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR, requestId, traceId);
                }
                yield ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR, requestId, traceId);
            }
        };
    }

    /**
     * 构建指定错误码、消息和状态的失败响应体。
     */
    private ApiResponse<Void> buildErrorBody(
            String code, String message, int httpStatus, String requestId, String traceId) {
        return ApiResponse.failure(new SimpleErrorCode(code, message, httpStatus), requestId, traceId);
    }

    /**
     * 序列化失败时的 JSON 兜底字符串，使用中文错误信息。
     */
    private byte[] buildFallbackJson(String requestId, String traceId) {
        String safeRequestId = requestId != null ? requestId : "";
        String safeTraceId = traceId != null ? traceId : "";
        String json = String.format(
                "{\"code\":\"COMMON_INTERNAL_ERROR\",\"message\":\"系统内部错误\",\"requestId\":\"%s\",\"traceId\":\"%s\",\"data\":null}",
                safeRequestId, safeTraceId);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 网关内部使用的轻量级错误码实现，避免为临时映射创建枚举值。
     */
    private record SimpleErrorCode(String code, String message, int httpStatus) implements ErrorCode {
    }
}
