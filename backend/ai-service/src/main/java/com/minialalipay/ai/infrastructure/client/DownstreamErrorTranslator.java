package com.minialalipay.ai.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 下游 4xx 错误体解析器（三个下游 HTTP 客户端共用）。
 *
 * <p>为什么解析响应体：下游 Controller 统一以 {@code ApiResponse} 信封返回错误
 * （{@code success:false, code, message}），其中 message 是面向用户的中文业务原因
 * （余额不足、金额超限等）。旧实现一律映射 {@code TOOL_UNAVAILABLE} 并丢弃该文案，
 * 导致业务错误全部显示为"服务暂不可用"。现在业务错误透传下游 message，
 * 仅响应体缺失或不可解析时才回退 {@code TOOL_UNAVAILABLE}。</p>
 *
 * <p>5xx 与 IO 异常不经过本类，仍由客户端映射 {@code TOOL_UNAVAILABLE}。</p>
 */
final class DownstreamErrorTranslator {

    private static final Logger log = LoggerFactory.getLogger(DownstreamErrorTranslator.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DownstreamErrorTranslator() {
    }

    /**
     * 将下游 4xx 响应转换为携带真实业务文案的异常。
     *
     * @param serviceName 下游服务名，仅用于日志定位
     * @param response RestClient onStatus 回调中的原始响应
     * @return 携带下游中文 message 的业务异常；响应体不可解析时回退 TOOL_UNAVAILABLE
     */
    static BusinessException translate(String serviceName, ClientHttpResponse response) {
        // getStatusCode/readBody 均声明抛 IOException，统一在 try 内处理，
        // 任何读取失败都回退 TOOL_UNAVAILABLE，不阻断错误处理链路
        int status;
        try {
            status = response.getStatusCode().value();
            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<?, ?> parsed = OBJECT_MAPPER.readValue(body, Map.class);
            Object code = parsed.get("code");
            Object message = parsed.get("message");
            if (message instanceof String text && !text.isBlank()) {
                // 保留下游原始错误码于日志，便于线上排查对应契约条目
                log.warn("下游业务错误透传: service={}, status={}, code={}, message={}",
                        serviceName, status, code, text);
                return new BusinessException(new DownstreamBusinessError(
                        code != null ? code.toString() : "DOWNSTREAM_ERROR", text, status));
            }
        } catch (Exception e) {
            log.warn("下游错误体解析失败，回退 TOOL_UNAVAILABLE: service={}, error={}",
                    serviceName, e.getMessage());
        }
        return new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
    }
}
