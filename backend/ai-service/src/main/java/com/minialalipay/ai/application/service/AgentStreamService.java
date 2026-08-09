package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.application.port.*;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.domain.agent.*;
import com.minialalipay.ai.infrastructure.client.RequestContext;
import com.minialalipay.common.error.BusinessException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AI Agent 流式消息处理服务。
 *
 * <p>与 {@link AgentMessageService} 共享相同的会话管理逻辑，
 * 但通过 {@link StreamCallback} 发射 SSE 事件实现流式输出。
 * 核心推理和工具调用委托给 {@link AgentLoop}（ReAct 主循环）。
 * 会话解析、上下文构建等公共逻辑委托给 {@link SessionContextHelper}。</p>
 *
 * <h3>与同步端点的关系</h3>
 * <ul>
 *   <li>{@code POST /messages}（同步）由 {@link AgentMessageService} 处理</li>
 *   <li>{@code POST /messages/stream}（SSE）由本服务处理</li>
 *   <li>两者共享相同的 Repository、AgentLoop 和 LLM 端口</li>
 * </ul>
 */
@Service
public class AgentStreamService {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamService.class);

    /** 流式文本分块大小（中文字符数），通过 ai.streaming.chunk-size 配置 */
    private final int chunkSize;
    /** 流式文本分块间隔（毫秒），通过 ai.streaming.chunk-delay-ms 配置 */
    private final long chunkDelayMs;
    /** 内容输出前的思考等待（毫秒），通过 ai.streaming.thinking-delay-ms 配置 */
    private final long thinkingDelayMs;

    /** 系统提示词，通过 ai.prompt.system 配置 */
    private final String systemPrompt;

    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final InjectionDetector injectionDetector;
    private final AgentLoop agentLoop;
    private final TaskExecutor taskExecutor;
    private final SessionContextHelper contextHelper;

    /** 会话级锁，保证同一会话串行处理 */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public AgentStreamService(
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository,
            LanguageModelPort languageModelPort,
            InjectionDetector injectionDetector,
            AgentLoop agentLoop,
            TaskExecutor taskExecutor,
            UserPreferenceService userPreferenceService,
            ObjectMapper objectMapper,
            @Value("${ai.session.timeout:30m}") String sessionTimeout,
            @Value("${ai.prompt.system:你是一只傲娇猫娘，名叫财喵，现在作为aialipay助手，你的职责是帮助用户完成转账、查余额、查交易、查花呗和还花呗等操作。重要规则：1.如果已经通过工具获取了某项数据（如余额、额度、交易记录），不要再次调用同一工具，直接基于已有结果生成回复。2.工具选择必须精确：用户问余额/多少钱→get_balance；用户问花呗额度/信用额度→get_credit_summary；用户问交易记录/流水→list_transactions；用户问花呗账单→list_credit_bills。不要混淆余额和额度。3.转账流程必须严格按顺序完成全部4步，不得中途停止生成文本：第一步调用search_payees搜索收款人（从用户消息中提取手机号或姓名作为query参数），第二步用返回的payeeId调用create_transfer_draft创建草稿，第三步调用validate_transfer_draft校验草稿，第四步调用prepare_confirmation_card生成确认卡片。每一步完成后必须立即调用下一步工具，不要在中间步骤生成文本回复。4.从用户消息中提取金额时，将元转换为分（如100元=10000分）。5.回复中禁止输出任何原始JSON数据、工具结果标记（如[TOOL_RESULT:xxx]）或resultCode等内部字段。6.提及收款人姓名时必须使用工具返回的完整姓名，不得截断或修改。7.系统不支持备注功能，回复中不得提及备注字段。}") String systemPrompt,
            @Value("${ai.streaming.chunk-size:5}") int chunkSize,
            @Value("${ai.streaming.chunk-delay-ms:50}") long chunkDelayMs,
            @Value("${ai.streaming.thinking-delay-ms:800}") long thinkingDelayMs
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.injectionDetector = injectionDetector;
        this.agentLoop = agentLoop;
        this.taskExecutor = taskExecutor;
        this.systemPrompt = systemPrompt;
        this.chunkSize = chunkSize;
        this.chunkDelayMs = chunkDelayMs;
        this.thinkingDelayMs = thinkingDelayMs;
        this.contextHelper = new SessionContextHelper(
                sessionRepository, messageRepository, languageModelPort,
                userPreferenceService, objectMapper,
                (int) SessionContextHelper.parseDurationMinutes(sessionTimeout));
    }

    /**
     * 异步处理消息并通过回调发射 SSE 事件。
     *
     * <p>在独立线程中执行完整的消息处理流程，包括会话管理、注入检测、
     * 幂等检查、AgentLoop 推理和工具执行。每个关键步骤通过回调
     * 发射对应的 SSE 事件。</p>
     *
     * @param userId 用户 ID
     * @param clientMessageId 客户端幂等键
     * @param sessionId 会话 ID（可空）
     * @param rawContent 用户输入原文
     * @param callback SSE 流式回调
     * @param bearerToken 当前请求的 Bearer Token，用于异步线程中透传下游鉴权
     */
    public void processMessageStream(
            String userId, String clientMessageId,
            String sessionId, String rawContent,
            String sanitizedContent,
            StreamCallback callback, String bearerToken
    ) {
        taskExecutor.execute(() -> {
            try {
                if (bearerToken != null && !bearerToken.isEmpty()) {
                    RequestContext.setBearerToken(bearerToken);
                }
                doProcessStream(userId, clientMessageId, sessionId, rawContent, sanitizedContent, callback);
            } catch (Exception e) {
                log.error("流式消息处理异常: userId={}", userId, e);
                safeEmitError(callback, "INTERNAL_ERROR", "服务内部异常");
            } finally {
                RequestContext.clear();
            }
        });
    }

    /**
     * 流式处理核心逻辑，在异步线程中执行。
     */
    private void doProcessStream(
            String userId, String clientMessageId,
            String sessionId, String rawContent,
            String sanitizedContent,
            StreamCallback callback
    ) {
        Instant now = Instant.now();

        // 1. 获取或创建会话
        AgentSession session = contextHelper.resolveSession(userId, sessionId, now);

        // 2. 获取会话锁
        ReentrantLock lock = sessionLocks.computeIfAbsent(
                session.getSessionId(), k -> new ReentrantLock());
        boolean acquired = false;
        try {
            try {
                acquired = lock.tryLock(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                safeEmitError(callback, "AGENT_BUSY", "系统繁忙，请稍后重试");
                return;
            }
            if (!acquired) {
                safeEmitError(callback, "AGENT_BUSY", "系统繁忙，请稍后重试");
                return;
            }

            // 3. 注入检测
            InjectionDetector.InjectionCheckResult injectionCheck =
                    injectionDetector.check(rawContent);
            if (!injectionCheck.safe()) {
                log.warn("流式提示注入被拒绝: userId={}, pattern={}",
                        userId, injectionCheck.detectedPattern());
                safeEmitError(callback, "PROMPT_INJECTION_REJECTED", "检测到不安全的输入内容");
                return;
            }

            // 4. 幂等检查
            Optional<AgentMessage> existingUser = messageRepository.findByClientMessageId(
                    session.getSessionId(), clientMessageId, MessageRole.USER);
            if (existingUser.isPresent()) {
                Optional<AgentMessage> existingAssistant = messageRepository.findByClientMessageId(
                        session.getSessionId(), clientMessageId, MessageRole.ASSISTANT);
                String cachedContent = existingAssistant
                        .map(AgentMessage::getContentRedacted)
                        .orElse("正在处理您的请求……");
                emitContentDeltas(cachedContent, callback);
                callback.onDone(existingUser.get().getMessageId(),
                        session.getSessionId(), "UNKNOWN");
                return;
            }

            // 5. 发射意图理解状态
            callback.onStatus("INTENT", "正在理解您的意图…");

            // 6. 保存用户消息（使用脱敏后内容存储，避免敏感信息进入数据库）
            String storeContent = (sanitizedContent != null && !sanitizedContent.isBlank())
                    ? sanitizedContent : rawContent;
            String messageId = AiServiceUtils.generateUlid();
            AgentMessage userMessage = new AgentMessage(
                    messageId, session.getSessionId(), clientMessageId,
                    MessageRole.USER, storeContent, AiServiceUtils.estimateTokens(storeContent), now);
            messageRepository.insert(userMessage);
            session.touch(now);

            // 7. 构建上下文并调用 AgentLoop（使用原始内容，LLM 需要真实参数调用工具）
            List<ChatMessage> context = contextHelper.buildContext(session, rawContent);
            List<ChatMessage> history = context.subList(0, context.size() - 1);

            AgentLoop.AgentContext agentContext = new AgentLoop.AgentContext(
                    userId, session.getSessionId(), rawContent,
                    history, session, systemPrompt, callback);

            AgentLoop.AgentResult agentResult = agentLoop.executeStreaming(agentContext);

            String finalContent = agentResult.finalContent();
            String pendingText = agentResult.pendingText();
            Map<String, Object> finalSlots = agentResult.accumulatedSlots();

            // 7.5 记录最近完成的操作，防止 LLM 重复提及已完成任务
            if (finalSlots == null) {
                finalSlots = new HashMap<>();
            }
            String completedAction = contextHelper.inferCompletedAction(agentResult.executedTools());
            if (completedAction != null) {
                finalSlots.put("lastCompletedAction", completedAction);
            }

            // 8. 流式推送内容：先推送过渡文本，再推送最终内容
            callback.onStatus("GENERATING", "正在生成回复…");
            try {
                Thread.sleep(thinkingDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (pendingText != null && !pendingText.isBlank()) {
                emitContentDeltas(AiServiceUtils.sanitizeContent(pendingText), callback);
            }
            emitContentDeltas(finalContent, callback);

            // 9. 保存 AI 回复（使用当前时间确保排序在用户消息之后）
            String assistantMessageId = AiServiceUtils.generateUlid();
            String fullAssistantContent = (pendingText != null && !pendingText.isBlank())
                    ? pendingText + "\n" + finalContent : finalContent;
            AgentMessage assistantMessage = new AgentMessage(
                    assistantMessageId, session.getSessionId(), clientMessageId,
                    MessageRole.ASSISTANT, fullAssistantContent, agentResult.totalTokens(), Instant.now());
            messageRepository.insert(assistantMessage);

            // 9.5 保存工具结果消息（用于历史消息恢复时重建卡片）
            contextHelper.saveToolResultMessages(agentResult.toolResults(), session.getSessionId(),
                    clientMessageId, assistantMessage.getCreatedAt());

            // 10. 更新会话状态
            if (finalSlots != null && !finalSlots.isEmpty()) {
                session.updateSlots(finalSlots);
            }

            // 11. 上下文压缩（Token 超限或对话超过 10 轮时触发四部分结构化压缩）
            long totalTokens = contextHelper.estimateContextTokens(context, agentResult.totalTokens());
            if (contextHelper.needsCompression(session.getSessionId(), totalTokens)) {
                String summary = contextHelper.compressContext(context);
                session.updateSummary(summary);
            }

            session.touch(now);
            sessionRepository.save(session);

            // 12. 推导意图并完成
            IntentType inferredIntent = contextHelper.inferIntent(agentResult.executedTools());
            callback.onDone(assistantMessageId, session.getSessionId(),
                    inferredIntent.name());

        } catch (BusinessException e) {
            log.warn("流式处理业务异常: userId={}, errorCode={}", userId, e.errorCode());
            safeEmitError(callback, e.errorCode().code(), e.errorCode().message());
        } catch (Exception e) {
            log.error("流式处理异常: userId={}", userId, e);
            safeEmitError(callback, "INTERNAL_ERROR", "服务内部异常");
        } finally {
            if (acquired) {
                lock.unlock();
            }
            // 不清理锁：cleanUpLock 存在 check-then-act 竞态条件，
            // 锁数量等于会话数量，内存开销可控。
        }
    }

    // ---- 流式文本分块推送 ----

    private void emitContentDeltas(String fullText, StreamCallback callback) {
        if (fullText == null || fullText.isBlank()) return;
        int len = fullText.length();
        for (int i = 0; i < len; i += chunkSize) {
            String delta = fullText.substring(i, Math.min(i + chunkSize, len));
            callback.onContentDelta(delta);
            if (i + chunkSize < len) {
                try {
                    Thread.sleep(chunkDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void safeEmitError(StreamCallback callback, String code, String message) {
        try {
            callback.onError(code, message);
        } catch (Exception ignored) {
        }
    }
}
