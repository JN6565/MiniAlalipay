package com.minialalipay.ai.interfaces.web;

import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.IOSanitizer;
import com.minialalipay.ai.application.service.AgentMessageService;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.domain.agent.AgentMessage;
import com.minialalipay.ai.domain.agent.AgentMessageRepository;
import com.minialalipay.ai.domain.agent.AgentSession;
import com.minialalipay.ai.domain.agent.AgentSessionRepository;
import com.minialalipay.ai.domain.agent.MessageRole;
import com.minialalipay.ai.infrastructure.client.RequestContext;
import com.minialalipay.ai.interfaces.web.dto.MessageSummaryResponse;
import com.minialalipay.ai.interfaces.web.dto.RenameSessionRequest;
import com.minialalipay.ai.interfaces.web.dto.SendMessageRequest;
import com.minialalipay.ai.interfaces.web.dto.SendMessageResponse;
import com.minialalipay.ai.interfaces.web.dto.SessionSummaryResponse;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final java.time.Clock clock;

    public AgentController(
            AgentMessageService agentMessageService,
            InjectionDetector injectionDetector,
            IOSanitizer sanitizer,
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository,
            java.time.Clock clock
    ) {
        this.agentMessageService = agentMessageService;
        this.injectionDetector = injectionDetector;
        this.sanitizer = sanitizer;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
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

        // 提取 Bearer Token 用于下游调用时经过网关鉴权
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            RequestContext.setBearerToken(authHeader.substring("Bearer ".length()));
        }

        try {
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

            // 2. 内容脱敏（脱敏后内容仅用于存储，原始内容传给应用层供 LLM 工具调用）
            String sanitizedContent = sanitizer.sanitizeContent(request.content());

            // 3. 委托应用服务（传入原始内容和脱敏内容）
            AgentMessageService.SendMessageResult result = agentMessageService.processMessage(
                    userId, request.clientMessageId(),
                    request.sessionId(), request.content(), sanitizedContent, clock.instant());

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
        } finally {
            RequestContext.clear();
        }
    }

    /**
     * 查询当前用户的活跃会话列表。
     *
     * <p>按最后活跃时间倒序返回，前端可用于渲染历史会话侧边栏。
     * 每条会话附带首条用户消息作为标题。</p>
     *
     * @param userId 用户 ID（由网关从会话令牌解析后注入）
     * @param httpRequest 原始 HTTP 请求（用于获取 Trace ID）
     * @return 会话摘要列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<List<SessionSummaryResponse>>> listSessions(
            @RequestHeader("X-User-Id") String userId,
            HttpServletRequest httpRequest
    ) {
        String requestId = resolveRequestId(httpRequest);
        String traceId = MDC.get("traceId");

        try {
            log.info("查询会话列表: userId={}", userId);
            List<AgentSession> sessions = sessionRepository.findActiveByUserId(userId);
            log.info("查询到 {} 个活跃会话", sessions.size());
            List<SessionSummaryResponse> result = new ArrayList<>(sessions.size());
            for (AgentSession s : sessions) {
                // 优先使用用户自定义标题，否则取首条用户消息作为标题
                String title = s.getTitle();
                if (title == null || title.isBlank()) {
                    List<AgentMessage> messages = messageRepository.findBySessionId(s.getSessionId(), 2);
                    title = "新会话";
                    for (AgentMessage m : messages) {
                        if (m.getRole() == MessageRole.USER) {
                            title = m.getContentRedacted();
                            if (title.length() > 30) {
                                title = title.substring(0, 30) + "…";
                            }
                            break;
                        }
                    }
                    // 统计消息条数
                    int messageCount = messages.size();
                    result.add(new SessionSummaryResponse(
                            s.getSessionId(), title,
                            DateTimeFormatter.ISO_INSTANT.format(s.getLastActiveAt()),
                            messageCount, s.getStatus().name()));
                } else {
                    List<AgentMessage> messages = messageRepository.findBySessionId(s.getSessionId(), 2);
                    result.add(new SessionSummaryResponse(
                            s.getSessionId(), title,
                            DateTimeFormatter.ISO_INSTANT.format(s.getLastActiveAt()),
                            messages.size(), s.getStatus().name()));
                }
            }
            return ResponseEntity.ok(ApiResponse.success(result, requestId, traceId));
        } catch (Exception e) {
            log.error("查询会话列表失败: userId={}, error={}", userId, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 查询指定会话的消息历史。
     *
     * <p>按创建时间正序返回全部消息，供前端恢复历史对话视图。
     * 会话归属校验：仅会话所有者可以查看。</p>
     *
     * @param userId 用户 ID（由网关从会话令牌解析后注入）
     * @param sessionId 会话 ID
     * @param httpRequest 原始 HTTP 请求（用于获取 Trace ID）
     * @return 消息列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<List<MessageSummaryResponse>>> getSessionMessages(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String sessionId,
            HttpServletRequest httpRequest
    ) {
        String requestId = resolveRequestId(httpRequest);
        String traceId = MDC.get("traceId");

        AgentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
        }

        List<AgentMessage> messages = messageRepository.findBySessionId(sessionId, 200);
        List<MessageSummaryResponse> result = new ArrayList<>(messages.size());
        for (AgentMessage m : messages) {
            result.add(new MessageSummaryResponse(
                    m.getMessageId(), m.getRole().name(),
                    m.getContentRedacted(),
                    DateTimeFormatter.ISO_INSTANT.format(m.getCreatedAt()),
                    m.getKind().name(),
                    m.getToolName()));
        }
        return ResponseEntity.ok(ApiResponse.success(result, requestId, traceId));
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerValue = request.getHeader(HEADER_REQUEST_ID);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        return java.util.UUID.randomUUID().toString();
    }

    /**
     * 软删除会话：将状态设为 CLOSED，不再出现在活跃会话列表中。
     *
     * <p>软删除而非物理删除，保留审计日志和消息历史的完整性。
     * 会话归属校验：仅会话所有者可以删除。</p>
     *
     * @param userId 用户 ID（由网关注入）
     * @param sessionId 会话 ID
     * @param httpRequest 原始 HTTP 请求
     * @return 删除结果
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String sessionId,
            HttpServletRequest httpRequest
    ) {
        String requestId = resolveRequestId(httpRequest);
        String traceId = MDC.get("traceId");

        // 归属校验：仅会话所有者可以删除
        AgentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
        }

        log.info("删除会话: userId={}, sessionId={}", userId, sessionId);
        boolean closed = sessionRepository.closeSession(sessionId);
        if (!closed) {
            throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
        }
        return ResponseEntity.ok(ApiResponse.success(null, requestId, traceId));
    }

    /**
     * 重命名会话：更新用户自定义标题。
     *
     * <p>标题为空字符串时清除自定义标题，前端回退到首条消息摘要。
     * 会话归属校验：仅会话所有者可以重命名。</p>
     *
     * @param userId 用户 ID（由网关注入）
     * @param sessionId 会话 ID
     * @param request 重命名请求
     * @param httpRequest 原始 HTTP 请求
     * @return 更新结果
     */
    @PatchMapping("/sessions/{sessionId}/title")
    public ResponseEntity<ApiResponse<Void>> renameSession(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String sessionId,
            @Valid @RequestBody RenameSessionRequest request,
            HttpServletRequest httpRequest
    ) {
        String requestId = resolveRequestId(httpRequest);
        String traceId = MDC.get("traceId");

        // 归属校验
        AgentSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.SESSION_NOT_FOUND));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
        }

        log.info("重命名会话: userId={}, sessionId={}, title={}", userId, sessionId, request.title());
        sessionRepository.updateTitle(sessionId, request.title());
        return ResponseEntity.ok(ApiResponse.success(null, requestId, traceId));
    }
}
