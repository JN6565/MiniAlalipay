package com.minialalipay.ai.interfaces.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.ai.application.port.SseEvent;
import com.minialalipay.ai.application.port.StreamCallback;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.IOSanitizer;
import com.minialalipay.ai.application.service.AgentStreamService;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.infrastructure.client.RequestContext;
import com.minialalipay.ai.interfaces.web.dto.SendMessageRequest;
import com.minialalipay.common.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI Agent SSE 流式对话控制器。
 *
 * <p>新增的流式端点，复用 AgentMessageService 核心逻辑并通过
 * {@link StreamCallback} → {@link SseEmitter} 逐事件推送到客户端。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>与同步端点的安全约束完全一致</li>
 *   <li>SSE 事件不传输支付密码、确认令牌或完整账号</li>
 *   <li>SseEmitter 超时 60s，超时后自动关闭连接</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentStreamController {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamController.class);

    private final AgentStreamService agentStreamService;
    private final InjectionDetector injectionDetector;
    private final IOSanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final ExecutorService streamExecutor;

    public AgentStreamController(
            AgentStreamService agentStreamService,
            InjectionDetector injectionDetector,
            IOSanitizer sanitizer,
            ObjectMapper objectMapper,
            @Value("${ai.stream.thread-pool-size:4}") int poolSize
    ) {
        this.agentStreamService = agentStreamService;
        this.injectionDetector = injectionDetector;
        this.sanitizer = sanitizer;
        this.objectMapper = objectMapper;
        this.streamExecutor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "sse-stream-");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * AI Agent SSE 流式对话端点。
     *
     * <p>返回 {@link SseEmitter}，通过独立线程执行流式处理并逐事件发送。
     * 事件类型包括：agent-status、agent-tool-call、agent-tool-result、
     * agent-content、agent-confirmation、agent-clarification、agent-done、
     * agent-error。</p>
     *
     * @param userId 用户 ID（由网关从会话令牌解析后注入）
     * @param request 客户端消息请求
     * @param httpRequest 原始 HTTP 请求（用于提取 Bearer Token）
     * @return SseEmitter 流式响应
     */
    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest
    ) {
        // 提取 Bearer Token（仅作局部变量，不在 HTTP 线程设置 RequestContext，防止 ThreadLocal 泄露）
        String authHeader = httpRequest.getHeader("Authorization");
        final String bearerToken = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring("Bearer ".length()) : null;

        // 注入检测
        InjectionDetector.InjectionCheckResult check = injectionDetector.check(request.content());
        if (!check.safe()) {
            log.warn("SSE 流注入被拒绝: userId={}, detectedPattern={}",
                    userId, check.detectedPattern());
            throw new BusinessException(AgentErrorCode.PROMPT_INJECTION_REJECTED);
        }

        String sanitizedContent = sanitizer.sanitizeContent(request.content());

        SseEmitter emitter = new SseEmitter(60_000L);

        streamExecutor.execute(() -> {
            try {
                // 仅在 worker 线程设置 RequestContext，finally 块会清理，不会泄露到 HTTP 线程
                RequestContext.setBearerToken(bearerToken);

                StreamCallback callback = new SseStreamCallback(emitter, objectMapper);
                agentStreamService.processStream(
                        userId, request.clientMessageId(),
                        request.sessionId(), sanitizedContent, callback);
                emitter.complete();
            } catch (BusinessException e) {
                // BusinessException 已在 processMessageWithStream 中通过 callback.onError 通知客户端，
                // 此处只做优雅关闭，不重复发送 agent-error 事件
                log.warn("SSE 业务异常: {}", e.getMessage());
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 流异常: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("agent-error")
                            .data(objectMapper.writeValueAsString(
                                    new SseEvent.ErrorPayload("INTERNAL_ERROR", "处理异常"))));
                    emitter.complete();
                } catch (IOException ignored) {
                    // 连接已断开，用 completeWithError 触发清理
                    emitter.completeWithError(e);
                }
            } finally {
                RequestContext.clear();
            }
        });

        // 连接关闭时清理
        emitter.onCompletion(() -> log.debug("SSE 客户端断开: userId={}", userId));
        emitter.onTimeout(() -> log.debug("SSE 超时: userId={}", userId));

        return emitter;
    }

    /**
     * StreamCallback → SseEmitter 适配器。
     *
     * <p>将 {@link StreamCallback} 的每种事件方法映射为对应的 SSE 事件名称，
     * 并通过 {@link SseEmitter#send(SseEmitter.SseEventBuilder)} 发送到客户端。</p>
     */
    private static class SseStreamCallback implements StreamCallback {
        private final SseEmitter emitter;
        private final ObjectMapper mapper;

        SseStreamCallback(SseEmitter emitter, ObjectMapper mapper) {
            this.emitter = emitter;
            this.mapper = mapper;
        }

        @Override public void onStatus(SseEvent.StatusPayload e) { emit("agent-status", e); }
        @Override public void onToolCall(SseEvent.ToolCallPayload e) { emit("agent-tool-call", e); }
        @Override public void onToolResult(SseEvent.ToolResultPayload e) { emit("agent-tool-result", e); }
        @Override public void onContentDelta(SseEvent.ContentPayload e) { emit("agent-content", e, 120); }
        @Override public void onConfirmation(SseEvent.ConfirmationPayload e) { emit("agent-confirmation", e); }
        @Override public void onClarification(SseEvent.ClarificationPayload e) { emit("agent-clarification", e); }
        @Override public void onDone(SseEvent.DonePayload e) { emit("agent-done", e); }
        @Override public void onError(SseEvent.ErrorPayload e) { emit("agent-error", e); }

        private void emit(String eventName, Object payload) { emit(eventName, payload, 0); }

        private void emit(String eventName, Object payload, int delayMs) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(mapper.writeValueAsString(payload)));
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
            } catch (IOException e) {
                log.warn("SSE 事件发送失败: event={}, error={}", eventName, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
