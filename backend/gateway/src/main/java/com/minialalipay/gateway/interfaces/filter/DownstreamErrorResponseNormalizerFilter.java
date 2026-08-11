package com.minialalipay.gateway.interfaces.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.reactivestreams.Publisher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 下游错误响应标准化过滤器。
 *
 * <p>当网关自身抛出异常（如连接失败、超时）时，{@code GatewayExceptionHandler} 负责
 * 统一封装响应。但当下游服务<b>成功返回</b> HTTP 错误响应（4xx/5xx）时，响应体直接
 * 透传给客户端，不经过异常处理器。如果下游返回的是 Spring Boot 默认错误格式
 * （如 {@code {timestamp, status, error, message, path}}），客户端收到的就不是
 * {@link ApiResponse}。</p>
 *
 * <p>本过滤器在响应阶段拦截下游的错误 JSON 响应，检查其是否已经是 {@code ApiResponse}
 * 格式（包含 {@code "code"} 字段）。如果不是，则将其改写为统一的 {@code ApiResponse}
 * 格式，确保前端收到的所有错误响应结构一致。</p>
 *
 * <h3>处理规则</h3>
 * <ul>
 *   <li>仅处理状态码为 4xx/5xx 且 Content-Type 为 JSON 的响应</li>
 *   <li>响应体已包含 {@code "code"} 字段时视为标准格式，直接透传</li>
 *   <li>非标准格式的 JSON 错误响应被改写为 {@code ApiResponse}，保留 HTTP 状态码</li>
 *   <li>尝试从下游响应体提取 {@code message} 字段作为辅助信息</li>
 *   <li>非 JSON 响应（如 HTML 错误页）使用通用错误码改写</li>
 * </ul>
 */
@Component
public final class DownstreamErrorResponseNormalizerFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorResponseNormalizerFilter.class);

    /**
     * ApiResponse 格式的判定标志：JSON 对象中包含 "code" 字段。
     *
     * <p>选择 {@code "code"} 而非 {@code "message"} 作为判定依据，因为 Spring Boot
     * 默认错误格式也包含 {@code "message"} 字段，而 {@code "code"} 是 {@code ApiResponse}
     * 独有的机器可读错误码字段。</p>
     */
    private static final String API_RESPONSE_CODE_MARKER = "\"code\"";

    private final ObjectMapper objectMapper;

    public DownstreamErrorResponseNormalizerFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(exchange.getResponse()) {

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                HttpStatusCode statusCode = getStatusCode();
                if (statusCode == null || !isErrorResponse(statusCode)) {
                    return super.writeWith(body);
                }

                MediaType contentType = getHeaders().getContentType();
                if (contentType != null && !isJsonCompatible(contentType)) {
                    return rewriteNonJsonError(exchange, this, statusCode);
                }

                ServerHttpResponse delegateResponse = getDelegate();
                return super.writeWith(
                        DataBufferUtils.join(Flux.from(body))
                                .flatMap(dataBuffer -> {
                                    byte[] originalBytes = new byte[dataBuffer.readableByteCount()];
                                    dataBuffer.read(originalBytes);
                                    DataBufferUtils.release(dataBuffer);
                                    String originalBody = new String(originalBytes, StandardCharsets.UTF_8);

                                    if (isAlreadyApiResponse(originalBody)) {
                                        DataBuffer preservedBuffer = delegateResponse.bufferFactory()
                                                .wrap(originalBytes);
                                        return Mono.just(preservedBuffer);
                                    }

                                    log.debug("下游错误响应格式非标准，已改写: status={}, originalSnippet={}",
                                            statusCode.value(),
                                            truncate(originalBody, 200));
                                    return rewriteToJsonApiResponse(
                                            exchange, delegateResponse, statusCode, originalBody);
                                })
                );
            }
        };

        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }

    @Override
    public int getOrder() {
        return GatewayFilterOrders.RESPONSE_NORMALIZER;
    }

    /**
     * 判断是否为需要标准化的错误响应状态码（4xx 或 5xx）。
     */
    private boolean isErrorResponse(HttpStatusCode statusCode) {
        return statusCode.is4xxClientError() || statusCode.is5xxServerError();
    }

    /**
     * 判断 Content-Type 是否为 JSON 或兼容类型（如 {@code application/problem+json}）。
     */
    private boolean isJsonCompatible(MediaType contentType) {
        return contentType.isCompatibleWith(MediaType.APPLICATION_JSON)
                || contentType.getSubtype().contains("json");
    }

    /**
     * 判断响应体是否已经是 {@code ApiResponse} 格式。
     *
     * <p>通过检测 JSON 对象中是否包含 {@code "code"} 字段来判定。
     * Spring Boot 默认错误格式包含 {@code timestamp/status/error/message/path}，
     * 不含 {@code "code"} 字段，因此会被正确识别为非标准格式。</p>
     */
    private boolean isAlreadyApiResponse(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("{")) {
            return false;
        }
        return trimmed.contains(API_RESPONSE_CODE_MARKER);
    }

    /**
     * 将非标准 JSON 错误响应改写为 {@code ApiResponse} 格式。
     *
     * <p>尝试从下游响应体解析 {@code message} 字段用于日志追踪。
     * 改写后的响应使用 {@link CommonErrorCode} 中对应 HTTP 状态码的标准错误码，
     * 不暴露下游内部实现细节。</p>
     */
    private Mono<DataBuffer> rewriteToJsonApiResponse(
            ServerWebExchange exchange,
            ServerHttpResponse response,
            HttpStatusCode statusCode,
            String originalBody) {

        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME);
        if (requestId == null) {
            requestId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID);
        }
        String traceId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_TRACE_ID);

        ErrorCode errorCode = mapHttpStatusToErrorCode(statusCode.value());
        ApiResponse<Void> apiResponse = ApiResponse.failure(errorCode, requestId, traceId);

        try {
            byte[] normalizedBytes = objectMapper.writeValueAsBytes(apiResponse);
            response.getHeaders().setContentLength(normalizedBytes.length);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return Mono.just(response.bufferFactory().wrap(normalizedBytes));
        } catch (Exception e) {
            log.warn("标准化下游错误响应序列化失败: requestId={}", requestId, e);
            byte[] fallback = buildFallbackJson(requestId, traceId);
            response.getHeaders().setContentLength(fallback.length);
            return Mono.just(response.bufferFactory().wrap(fallback));
        }
    }

    /**
     * 处理非 JSON 格式的错误响应（如 HTML 错误页）。
     *
     * <p>丢弃原始响应体，替换为统一的 {@code ApiResponse} JSON 格式。</p>
     */
    private Mono<Void> rewriteNonJsonError(
            ServerWebExchange exchange,
            ServerHttpResponseDecorator response,
            HttpStatusCode statusCode) {

        log.debug("下游返回非 JSON 错误响应，已改写: status={}, contentType={}",
                statusCode.value(), response.getHeaders().getContentType());

        String requestId = exchange.getRequest().getHeaders().getFirst(RequestIdGlobalFilter.HEADER_NAME);
        if (requestId == null) {
            requestId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_REQUEST_ID);
        }
        String traceId = exchange.getAttribute(RequestIdGlobalFilter.ATTR_TRACE_ID);

        ErrorCode errorCode = mapHttpStatusToErrorCode(statusCode.value());
        ApiResponse<Void> apiResponse = ApiResponse.failure(errorCode, requestId, traceId);

        try {
            byte[] normalizedBytes = objectMapper.writeValueAsBytes(apiResponse);
            response.getHeaders().setContentLength(normalizedBytes.length);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            DataBuffer buffer = response.bufferFactory().wrap(normalizedBytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.warn("标准化非 JSON 错误响应序列化失败: requestId={}", requestId, e);
            return Mono.error(e);
        }
    }

    /**
     * 将 HTTP 状态码映射为对应的 {@link CommonErrorCode}。
     *
     * <p>只映射客户端和服务器端常见错误码，其他状态码统一使用
     * {@link CommonErrorCode#INTERNAL_ERROR}。</p>
     */
    private ErrorCode mapHttpStatusToErrorCode(int httpStatus) {
        return switch (httpStatus) {
            case 400 -> CommonErrorCode.INVALID_REQUEST;
            case 401 -> CommonErrorCode.UNAUTHORIZED;
            case 403 -> CommonErrorCode.FORBIDDEN;
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
            case 409 -> new SimpleErrorCode("COMMON_CONFLICT", "资源冲突", 409);
            case 415 -> CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 422 -> new SimpleErrorCode("COMMON_UNPROCESSABLE", "请求无法处理", 422);
            case 429 -> CommonErrorCode.RATE_LIMITED;
            case 503 -> CommonErrorCode.SERVICE_UNAVAILABLE;
            case 504 -> CommonErrorCode.GATEWAY_TIMEOUT;
            default -> httpStatus >= 500
                    ? CommonErrorCode.INTERNAL_ERROR
                    : CommonErrorCode.INTERNAL_ERROR;
        };
    }

    /**
     * 序列化失败时的 JSON 兜底字符串。
     */
    private byte[] buildFallbackJson(String requestId, String traceId) {
        String safeRequestId = requestId != null ? requestId : "";
        String safeTraceId = traceId != null ? traceId : "";
        String json = String.format(
                "{\"code\":\"COMMON_INTERNAL_ERROR\",\"message\":\"系统内部错误\","
                        + "\"requestId\":\"%s\",\"traceId\":\"%s\",\"data\":null}",
                safeRequestId, safeTraceId);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 截断字符串用于日志输出，避免日志过长。
     */
    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }

    /**
     * 网关内部使用的轻量级错误码实现，用于没有预定义 {@link CommonErrorCode} 的 HTTP 状态码。
     */
    private record SimpleErrorCode(String code, String message, int httpStatus) implements ErrorCode {
    }
}
