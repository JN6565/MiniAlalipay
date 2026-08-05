package com.minialalipay.ai.interfaces.web;

import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.IOSanitizer;
import com.minialalipay.ai.application.service.AgentMessageService;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.interfaces.web.dto.SendMessageRequest;
import com.minialalipay.ai.interfaces.web.dto.SendMessageResponse;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;

/**
 * AI Agent 对话控制器。
 *
 * <p>对外暴露的唯一 AI 交互入口：{@code POST /api/v1/agent/messages}。
 * 用户身份由网关通过 {@code X-User-Id} 头注入，不信任请求体中的身份信息。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>{@code userId} 从 {@code X-User-Id} 读取，不由客户端提交</li>
 *   <li>请求 DTO 不含 principalId、付款账户、收款账户或确认上下文</li>
 *   <li>会话归属校验：仅会话所有者可以继续对话</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private static final String HEADER_REQUEST_ID = "X-Request-Id";

    private final AgentMessageService agentMessageService;
    private final InjectionDetector injectionDetector;
    private final IOSanitizer sanitizer;
    private final java.time.Clock clock;

    public AgentController(
            AgentMessageService agentMessageService,
            InjectionDetector injectionDetector,
            IOSanitizer sanitizer,
            java.time.Clock clock
    ) {
        this.agentMessageService = agentMessageService;
        this.injectionDetector = injectionDetector;
        this.sanitizer = sanitizer;
        this.clock = clock;
    }

    /**
     * AI 多轮对话入口。
     *
     * @param userId 用户 ID（由网关从会话令牌解析后注入）
     * @param request 客户端消息请求
     * @param httpRequest 原始 HTTP 请求（用于获取 Trace ID）
     * @return ApiResponse 信封包装的 AI 回复
     */
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<SendMessageResponse>> sendMessage(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest
    ) {
        String requestId = resolveRequestId(httpRequest);
        String traceId = MDC.get("traceId");

        log.debug("收到 AI 消息: userId={}, sessionId={}, clientMessageId={}",
                userId, request.sessionId(), request.clientMessageId());

        // 1. 注入检测
        InjectionDetector.InjectionCheckResult check =
                injectionDetector.check(request.content());
        if (!check.safe()) {
            log.warn("提示注入被拒绝: userId={}, detectedPattern={}",
                    userId, check.detectedPattern());
            throw new BusinessException(
                    AgentErrorCode.PROMPT_INJECTION_REJECTED,
                    java.util.Map.of("reason", check.reason(), "detectedPattern", check.detectedPattern())
            );
        }

        // 2. 内容脱敏
        String sanitizedContent = sanitizer.sanitizeContent(request.content());

        // 3. 委托应用服务
        AgentMessageService.SendMessageResult result = agentMessageService.processMessage(
                userId, request.clientMessageId(),
                request.sessionId(), sanitizedContent, clock.instant());

        // 4. 构建响应
        SendMessageResponse data = new SendMessageResponse(
                result.sessionId(),
                result.messageId(),
                result.content(),
                result.intent().name(),
                result.slots(),
                result.clarificationNeeded()
        );
        return ResponseEntity.ok(ApiResponse.success(data, requestId, traceId));
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerValue = request.getHeader(HEADER_REQUEST_ID);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        return java.util.UUID.randomUUID().toString();
    }
}
