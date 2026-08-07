package com.minialalipay.ai.interfaces.web;

import com.minialalipay.ai.application.port.StreamCallback;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.IOSanitizer;
import com.minialalipay.ai.application.service.AgentStreamService;
import com.minialalipay.ai.infrastructure.client.RequestContext;
import com.minialalipay.ai.interfaces.web.dto.SendMessageRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * AI Agent SSE 流式对话控制器。
 *
 * <p>提供 {@code POST /api/v1/agent/messages/stream} 端点，通过 Server-Sent Events
 * 流式推送 AI 回复。与同步端点 {@code POST /messages} 共享相同的请求 DTO 和安全约束。</p>
 *
 * <h3>SSE 事件协议</h3>
 * <ul>
 *   <li>{@code agent-status}：处理阶段状态更新</li>
 *   <li>{@code agent-tool-call}：工具调用开始</li>
 *   <li>{@code agent-tool-result}：工具调用结果</li>
 *   <li>{@code agent-content}：文本内容增量</li>
 *   <li>{@code agent-clarification}：澄清引导</li>
 *   <li>{@code agent-done}：流式完成</li>
 *   <li>{@code agent-error}：错误</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentStreamController {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamController.class);

    /** SSE 超时：60 秒（LLM 推理可能较慢） */
    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final AgentStreamService agentStreamService;
    private final InjectionDetector injectionDetector;
    private final IOSanitizer sanitizer;

    public AgentStreamController(
            AgentStreamService agentStreamService,
            InjectionDetector injectionDetector,
            IOSanitizer sanitizer
    ) {
        this.agentStreamService = agentStreamService;
        this.injectionDetector = injectionDetector;
        this.sanitizer = sanitizer;
    }

    /**
     * SSE 流式对话端点。
     *
     * <p>接收用户消息后返回 SSE 流，逐步推送处理状态、工具调用和 AI 回复内容。
     * 客户端使用 {@code fetch + ReadableStream} 消费（EventSource 不支持 POST）。</p>
     *
     * @param userId 用户 ID（由网关从会话令牌解析后注入）
     * @param request 客户端消息请求
     * @return SseEmitter 流式事件发射器
     */
    @PostMapping(value = "/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SendMessageRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        // 禁止代理和中间件缓冲 SSE 流：确保每个事件立即送达客户端
        httpResponse.setHeader("Cache-Control", "no-cache, no-transform");
        httpResponse.setHeader("X-Accel-Buffering", "no");
        httpResponse.setHeader("Connection", "keep-alive");

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 提取 Bearer Token：Servlet 线程设置用于同步路径，异步线程通过参数显式传递
        String authHeader = httpRequest.getHeader("Authorization");
        String bearerToken = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            bearerToken = authHeader.substring("Bearer ".length());
            RequestContext.setBearerToken(bearerToken);
        }

        try {
            // 注入检测（前置快速拒绝，避免建立无效 SSE 连接）
            InjectionDetector.InjectionCheckResult check =
                    injectionDetector.check(request.content());
            if (!check.safe()) {
                log.warn("流式端点注入检测拒绝: userId={}, pattern={}",
                        userId, check.detectedPattern());
                try {
                    emitter.send(SseEmitter.event()
                            .name("agent-error")
                            .data(Map.of(
                                    "code", "PROMPT_INJECTION_REJECTED",
                                    "message", "检测到不安全的输入内容")));
                    emitter.complete();
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
                return emitter;
            }

            // 内容脱敏
            String sanitizedContent = sanitizer.sanitizeContent(request.content());

            // 构建回调：将 StreamCallback 事件转为 SSE 事件
            StreamCallback callback = buildCallback(emitter, userId);

            // 异常和超时回调
            emitter.onCompletion(() -> log.debug("SSE 连接完成: userId={}", userId));
            emitter.onTimeout(() -> {
                log.warn("SSE 连接超时: userId={}", userId);
                emitter.complete();
            });
            emitter.onError(ex -> log.debug("SSE 连接异常: userId={}", userId, ex));

            // 异步执行流式处理：显式传递 bearerToken，避免线程池复用时 InheritableThreadLocal 丢失
            agentStreamService.processMessageStream(
                    userId, request.clientMessageId(),
                    request.sessionId(), sanitizedContent, callback, bearerToken);

            return emitter;
        } finally {
            // Servlet 线程清理上下文，异步线程由 AgentStreamService 自行清理
            RequestContext.clear();
        }
    }

    /**
     * 构建 SSE 回调适配器，将 StreamCallback 方法转换为 SseEmitter 事件。
     * 每个方法内部捕获 IOException，避免单次发送失败导致整个流程中断。
     */
    private StreamCallback buildCallback(SseEmitter emitter, String userId) {
        return new StreamCallback() {
            @Override
            public void onStatus(String stage, String message) {
                sendEvent("agent-status", Map.of("stage", stage, "message", message));
            }

            @Override
            public void onToolCall(String toolName, String status) {
                sendEvent("agent-tool-call", Map.of("tool", toolName, "status", status));
            }

            @Override
            public void onToolResult(String toolName, String status, String summary, Map<String, Object> data) {
                sendEvent("agent-tool-result",
                        Map.of("tool", toolName, "status", status, "summary", summary, "data", data != null ? data : Map.of()));
            }

            @Override
            public void onContentDelta(String delta) {
                sendEvent("agent-content", Map.of("delta", delta));
            }

            @Override
            public void onClarification(String question, List<ClarificationOption> options) {
                sendEvent("agent-clarification",
                        Map.of("question", question, "options", options));
            }

            @Override
            public void onDone(String messageId, String sessionId, String intent) {
                try {
                    sendEvent("agent-done",
                            Map.of("messageId", messageId,
                                    "sessionId", sessionId, "intent", intent));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onError(String code, String message) {
                try {
                    sendEvent("agent-error", Map.of("code", code, "message", message));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }

            private void sendEvent(String eventName, Object data) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                } catch (IOException | IllegalStateException e) {
                    log.debug("SSE 事件发送失败（客户端可能已断开）: event={}, userId={}",
                            eventName, userId);
                }
            }
        };
    }
}
