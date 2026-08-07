package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.*;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.ToolAuditService;
import com.minialalipay.ai.domain.agent.*;
import com.minialalipay.ai.infrastructure.client.RequestContext;
import com.minialalipay.ai.infrastructure.client.ToolRouter;
import com.minialalipay.common.error.BusinessException;

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
 * <p>与 {@link AgentMessageService} 共享相同的会话管理和工具执行逻辑，
 * 但在处理过程中通过 {@link StreamCallback} 发射 SSE 事件，实现流式输出。</p>
 *
 * <h3>与同步端点的关系</h3>
 * <ul>
 *   <li>{@code POST /messages}（同步）由 {@link AgentMessageService} 处理</li>
 *   <li>{@code POST /messages/stream}（SSE）由本服务处理</li>
 *   <li>两者共享相同的 Repository、ToolRouter 和 LLM 端口</li>
 * </ul>
 */
@Service
public class AgentStreamService {

    private static final Logger log = LoggerFactory.getLogger(AgentStreamService.class);

    /** 上下文窗口：保留最近 10 轮对话 */
    private static final int CONTEXT_TURN_LIMIT = 10;
    private static final int CONTEXT_MESSAGE_LIMIT = CONTEXT_TURN_LIMIT * 2;
    private static final int COMPRESSION_THRESHOLD = CONTEXT_TURN_LIMIT * 2 + 4;
    private static final int MAX_CONTEXT_TOKENS = 4096;

    /** GAP-3：大额风险提示阈值（分），单笔转账 ≥ 此值时主动确认 */
    private static final long LARGE_AMOUNT_THRESHOLD_FEN = 500_000L;

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
    private final LanguageModelPort languageModelPort;
    private final InjectionDetector injectionDetector;
    private final ToolRouter toolRouter;
    private final ToolPolicyService toolPolicy;
    private final ToolAuditService toolAudit;
    private final ResultInterpreter resultInterpreter;
    private final TaskExecutor taskExecutor;
    private final boolean mockMode;
    private final int sessionTimeoutMinutes;
    private final UserPreferenceService userPreferenceService;

    /** 会话级锁，保证同一会话串行处理 */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public AgentStreamService(
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository,
            LanguageModelPort languageModelPort,
            InjectionDetector injectionDetector,
            ToolRouter toolRouter,
            ToolPolicyService toolPolicy,
            ToolAuditService toolAudit,
            ResultInterpreter resultInterpreter,
            TaskExecutor taskExecutor,
            UserPreferenceService userPreferenceService,
            @Value("${ai.session.timeout:30m}") String sessionTimeout,
            @Value("${ai.llm.mock-mode:true}") boolean mockMode,
            @Value("${ai.prompt.system:你是一只傲娇猫娘，名叫吱托芙，现在作为aialipay助手，你的职责是帮助用户完成转账、查余额、查交易、查花呗和还花呗等操作。}") String systemPrompt,
            @Value("${ai.streaming.chunk-size:2}") int chunkSize,
            @Value("${ai.streaming.chunk-delay-ms:120}") long chunkDelayMs,
            @Value("${ai.streaming.thinking-delay-ms:800}") long thinkingDelayMs
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.languageModelPort = languageModelPort;
        this.injectionDetector = injectionDetector;
        this.toolRouter = toolRouter;
        this.toolPolicy = toolPolicy;
        this.toolAudit = toolAudit;
        this.resultInterpreter = resultInterpreter;
        this.taskExecutor = taskExecutor;
        this.userPreferenceService = userPreferenceService;
        this.sessionTimeoutMinutes = (int) parseDurationMinutes(sessionTimeout);
        this.mockMode = mockMode;
        this.systemPrompt = systemPrompt;
        this.chunkSize = chunkSize;
        this.chunkDelayMs = chunkDelayMs;
        this.thinkingDelayMs = thinkingDelayMs;
    }

    /**
     * 异步处理消息并通过回调发射 SSE 事件。
     *
     * <p>在独立线程中执行完整的消息处理流程，包括会话管理、注入检测、
     * 幂等检查、LLM 推理、工具执行和结果解释。每个关键步骤通过回调
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
            StreamCallback callback, String bearerToken
    ) {
        taskExecutor.execute(() -> {
            try {
                // 手动将 Servlet 线程的 Bearer Token 传播到异步线程
                // InheritableThreadLocal 与线程池复用线程不兼容，必须显式传递
                if (bearerToken != null && !bearerToken.isEmpty()) {
                    RequestContext.setBearerToken(bearerToken);
                }
                doProcessStream(userId, clientMessageId, sessionId, rawContent, callback);
            } catch (Exception e) {
                log.error("流式消息处理异常: userId={}", userId, e);
                safeEmitError(callback, "INTERNAL_ERROR", "服务内部异常");
            } finally {
                // 异步线程执行完毕后清理上下文，避免线程池复用时泄漏
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
            StreamCallback callback
    ) {
        Instant now = Instant.now();

        // 1. 获取或创建会话
        AgentSession session = resolveSession(userId, sessionId, now);

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

            // 6. 保存用户消息
            String messageId = generateUlid();
            AgentMessage userMessage = new AgentMessage(
                    messageId, session.getSessionId(), clientMessageId,
                    MessageRole.USER, rawContent, estimateTokens(rawContent), now);
            messageRepository.insert(userMessage);
            session.touch(now);

            // 7. 构建上下文并调用 LLM
            List<ChatMessage> context = buildContext(session, rawContent);
            ChatResponse llmResponse = languageModelPort.chat(
                    systemPrompt, context.subList(0, context.size() - 1),
                    context.get(context.size() - 1).content());

            // 8. 澄清分支：需要澄清时直接发射澄清事件
            if (llmResponse.clarificationNeeded()) {
                List<StreamCallback.ClarificationOption> options =
                        deriveClarificationOptions(llmResponse.slots());
                callback.onClarification(llmResponse.content(), options);

                // 保存助手消息
                String assistantMsgId = generateUlid();
                AgentMessage assistantMsg = new AgentMessage(
                        assistantMsgId, session.getSessionId(), clientMessageId,
                        MessageRole.ASSISTANT, llmResponse.content(),
                        llmResponse.tokenCount(), now);
                messageRepository.insert(assistantMsg);
                session.touch(now);
                sessionRepository.save(session);

                callback.onDone(assistantMsgId, session.getSessionId(),
                        llmResponse.intent().name());
                return;
            }

            // 9. 工具执行分支
            String finalContent;
            Map<String, Object> finalSlots;

            if (shouldExecuteTools(llmResponse)) {
                // 转账意图前置校验：逐项检查必填槽位（收款人 → 金额），并做异常金额风险提示
                if (llmResponse.intent() == IntentType.TRANSFER) {
                    // GAP-9：收款人缺失时单独追问，不混同于金额追问
                    Object queryObj = llmResponse.slots().get("query");
                    if (queryObj == null || queryObj.toString().isBlank()) {
                        log.info("转账意图收款人缺失，引导用户补充: userId={}", userId);
                        callback.onClarification("请问您要转账给谁？请提供收款人姓名或手机号。", List.of());
                        String assistId = generateUlid();
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, "请问您要转账给谁？请提供收款人姓名或手机号。",
                                0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        callback.onDone(assistId, session.getSessionId(),
                                llmResponse.intent().name());
                        return;
                    }
                    long amountFen = 0L;
                    Object amountObj = llmResponse.slots().get("amountFen");
                    if (amountObj instanceof Number) {
                        amountFen = ((Number) amountObj).longValue();
                    }
                    if (amountFen <= 0) {
                        log.info("转账意图金额缺失或为 0，引导用户补充: userId={}", userId);
                        callback.onClarification("请问您要转账多少金额？", List.of());
                        String assistId = generateUlid();
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, "请问您要转账多少金额？",
                                0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        callback.onDone(assistId, session.getSessionId(),
                                llmResponse.intent().name());
                        return;
                    }
                    // GAP-3：异常大额风险提示——单笔转账 ≥ 5000 元时主动确认
                    if (amountFen >= LARGE_AMOUNT_THRESHOLD_FEN) {
                        log.info("大额转账风险提示: userId={}, amountFen={}", userId, amountFen);
                        callback.onClarification(
                                "本次转账金额为 " + formatFenDisplay(amountFen)
                                        + " 元，属于大额操作。请确认您确实要转账此金额。",
                                List.of(
                                        new StreamCallback.ClarificationOption("confirm", "确认转账"),
                                        new StreamCallback.ClarificationOption("cancel", "取消")));
                        String assistId = generateUlid();
                        String riskMsg = "⚠️ 大额转账提醒：本次转账金额为 "
                                + formatFenDisplay(amountFen) + " 元，请仔细核对收款人信息。";
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, riskMsg, 0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        callback.onDone(assistId, session.getSessionId(),
                                llmResponse.intent().name());
                        return;
                    }
                }
                // 花呗还款意图前置校验：PRD 要求禁止"全部还清"，必须指定具体金额
                if (llmResponse.intent() == IntentType.CREDIT_REPAYMENT) {
                    // 安全边界：检测用户原始消息是否包含"全部还清"关键词，强制拦截
                    String lowerRaw = rawContent.toLowerCase();
                    if (containsAny(lowerRaw, "全部还清", "全额还", "还清全部", "一次还清")) {
                        log.info("拦截全部还清请求: userId={}, rawContent={}", userId, rawContent);
                        callback.onClarification("抱歉，暂不支持全部还清功能。请告诉我您想还款的具体金额（如'还200元'）。", List.of());
                        String assistId = generateUlid();
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, "抱歉，暂不支持全部还清功能。请告诉我您想还款的具体金额（如'还200元'）。",
                                0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        callback.onDone(assistId, session.getSessionId(),
                                llmResponse.intent().name());
                        return;
                    }
                    // 金额必须大于 0
                    long amountFen = 0L;
                    Object amountObj = llmResponse.slots().get("amountFen");
                    if (amountObj instanceof Number) {
                        amountFen = ((Number) amountObj).longValue();
                    }
                    if (amountFen <= 0) {
                        log.info("还款意图金额缺失或为 0，引导用户补充: userId={}", userId);
                        callback.onClarification("请问您要还款多少金额？", List.of());
                        String assistId = generateUlid();
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, "请问您要还款多少金额？",
                                0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        callback.onDone(assistId, session.getSessionId(),
                                llmResponse.intent().name());
                        return;
                    }
                }

                String traceId = generateUlid();
                List<ToolExecution> toolResults = executeToolsStreaming(
                        llmResponse.intent(), llmResponse.slots(),
                        userId, session, traceId, callback);

                if (!toolResults.isEmpty()) {
                    ToolExecution primary = toolResults.get(toolResults.size() - 1);

                    // 合并工具返回数据到槽位
                    finalSlots = new java.util.HashMap<>(llmResponse.slots());
                    for (ToolExecution te : toolResults) {
                        if (te.toolResult() != null && te.toolResult().data() != null) {
                            finalSlots.putAll(te.toolResult().data());
                        }
                    }

                    if (mockMode) {
                        finalContent = resultInterpreter.interpret(
                                primary.toolName(), primary.toolResult());
                    } else {
                        callback.onStatus("GENERATING", "正在生成回复…");
                        String toolContext = formatToolResultsAsSystemMessage(toolResults);
                        context.add(new ChatMessage(MessageRole.SYSTEM, toolContext));
                        ChatResponse secondResponse = languageModelPort.chat(
                                systemPrompt, context.subList(0, context.size() - 1),
                                "请基于工具调用结果回复用户，使用自然语言。");
                        finalContent = secondResponse.content();
                    }
                } else {
                    finalContent = llmResponse.content();
                    finalSlots = llmResponse.slots();
                }
            } else {
                finalContent = llmResponse.content();
                finalSlots = llmResponse.slots();
            }

            // 10. 流式推送最终内容：先发射“正在生成”状态并等待思考延迟，模拟真实 LLM 推理体验
            callback.onStatus("GENERATING", "正在生成回复…");
            try {
                Thread.sleep(thinkingDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            emitContentDeltas(finalContent, callback);

            // 11. 保存 AI 回复
            String assistantMessageId = generateUlid();
            AgentMessage assistantMessage = new AgentMessage(
                    assistantMessageId, session.getSessionId(), clientMessageId,
                    MessageRole.ASSISTANT, finalContent, llmResponse.tokenCount(), now);
            messageRepository.insert(assistantMessage);

            // 12. 更新会话状态
            if (!finalSlots.isEmpty()) {
                session.updateSlots(finalSlots);
            }

            // 13. 上下文压缩
            long totalTokens = estimateContextTokens(context, llmResponse.tokenCount());
            if (totalTokens > MAX_CONTEXT_TOKENS) {
                String summary = compressContext(session, context);
                session.updateSummary(summary);
            }

            session.touch(now);
            sessionRepository.save(session);

            // 14. 完成
            callback.onDone(assistantMessageId, session.getSessionId(),
                    llmResponse.intent().name());

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
            cleanUpLock(session.getSessionId());
        }
    }

    // ---- 流式工具执行 ----

    /**
     * 带 SSE 回调的工具执行链。
     * 每个工具调用前后发射 agent-tool-call / agent-tool-result 事件。
     */
    private List<ToolExecution> executeToolsStreaming(
            IntentType intent, Map<String, Object> slots,
            String userId, AgentSession session, String traceId,
            StreamCallback callback
    ) {
        List<IntentToolMapping.ToolMapping> mappings =
                IntentToolMapping.getToolsForIntent(intent);
        if (mappings.isEmpty()) {
            return List.of();
        }

        List<ToolExecution> results = new java.util.ArrayList<>();
        Map<String, Object> cumulativeParams = new java.util.HashMap<>(slots);

        for (IntentToolMapping.ToolMapping mapping : mappings) {
            ToolPolicyService.PolicyDecision decision =
                    toolPolicy.evaluate(mapping.toolName(), session);
            if (!decision.allowed()) {
                log.warn("工具执行被策略拒绝: tool={}, reason={}",
                        mapping.toolName(), decision.reason());
                results.add(ToolExecution.skipped(mapping.toolName(), decision.reason()));
                if (mapping.isChainDependent()) {
                    break;
                }
                continue;
            }

            // 发射工具调用开始事件
            callback.onToolCall(mapping.toolName(), "running");

            Map<String, Object> params = extractParams(mapping, cumulativeParams, userId);

            long startMs = System.currentTimeMillis();
            ToolResult toolResult;
            try {
                toolResult = toolRouter.route(mapping.toolName(), params, userId);
            } catch (Exception e) {
                log.error("工具执行异常: tool={}", mapping.toolName(), e);
                toolResult = new ToolResult("TOOL_UNAVAILABLE", Map.of(),
                        "工具执行异常", 0);
            }
            int duration = (int) (System.currentTimeMillis() - startMs);

            toolAudit.audit(mapping.toolName(), params, toolResult.resultCode(),
                    session.getSessionId(), traceId, duration, Instant.now());

            results.add(ToolExecution.completed(mapping.toolName(), toolResult, params));

            // 发射工具结果事件（附带结构化数据供前端渲染卡片）
            String status = toolResult.isSuccess() ? "success" : "failed";
            String summary = toolResult.isSuccess()
                    ? resultInterpreter.interpret(mapping.toolName(), toolResult)
                    : (toolResult.errorMessage() != null ? toolResult.errorMessage() : "工具执行失败");
            Map<String, Object> resultData = toolResult.data() != null
                    ? new java.util.HashMap<>(toolResult.data()) : Map.of();
            callback.onToolResult(mapping.toolName(), status, summary, resultData);

            if (!toolResult.isSuccess()) {
                log.warn("工具执行失败，中止链条: tool={}, resultCode={}",
                        mapping.toolName(), toolResult.resultCode());
                break;
            }

            if (toolResult.data() != null && !toolResult.data().isEmpty()) {
                cumulativeParams.putAll(toolResult.data());
            }

            // GAP-5：转账流程中 search_payees 返回多个重名收款人时，
            // 中止工具链并发起澄清，让用户选择正确的收款人
            if ("search_payees".equals(mapping.toolName())
                    && toolResult.isSuccess() && toolResult.data() != null
                    && intent == IntentType.TRANSFER) {
                List<StreamCallback.ClarificationOption> dupes =
                        findDuplicatePayees(toolResult.data());
                if (!dupes.isEmpty()) {
                    callback.onClarification("找到多个同名收款人，请选择：", dupes);
                    break;
                }
            }
        }
        return results;
    }

    // ---- 流式文本分块推送 ----

    /**
     * 将完整文本按固定大小分块，逐块通过回调推送。
     * 模拟 LLM 流式输出的打字效果。
     */
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

    // ---- 复用 AgentMessageService 的核心逻辑 ----

    private AgentSession resolveSession(String userId, String sessionId, Instant now) {
        if (sessionId != null && !sessionId.isBlank()) {
            AgentSession existing = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BusinessException(AgentErrorCode.SESSION_NOT_FOUND));
            // PRD 要求：会话超时 30 分钟后未提交草稿失效
            if (existing.checkExpiry(now, sessionTimeoutMinutes)) {
                sessionRepository.save(existing);
                log.info("会话已超时失效: sessionId={}, lastActiveAt={}",
                        existing.getSessionId(), existing.getLastActiveAt());
                throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
            }
            if (!existing.isActive()) {
                throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
            }
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
            }
            return existing;
        }
        AgentSession newSession = new AgentSession(generateUlid(), userId, now);
        sessionRepository.save(newSession);
        return newSession;
    }

    private List<ChatMessage> buildContext(AgentSession session, String currentMessage) {
        List<ChatMessage> context = new ArrayList<>();
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            context.add(new ChatMessage(MessageRole.SYSTEM,
                    "【对话摘要】" + session.getSummary()));
        }
        if (!session.getSlots().isEmpty()) {
            context.add(new ChatMessage(MessageRole.SYSTEM,
                    "【当前槽位】" + session.getSlots()));
        }
        // GAP-6：注入用户偏好（如最近使用的收款人），为 LLM 提供个性化上下文
        try {
            userPreferenceService.getLastPayee(session.getUserId()).ifPresent(payee -> {
                String nickname = payee.getOrDefault("nickname", "");
                if (!nickname.isBlank()) {
                    context.add(new ChatMessage(MessageRole.SYSTEM,
                            "【用户偏好】最近转账收款人：" + nickname));
                }
            });
        } catch (Exception e) {
            log.debug("加载用户偏好失败，跳过：{}", e.getMessage());
        }
        // 防止上下文污染：要求 LLM 仅针对当前用户消息回复，不重复历史答案
        context.add(new ChatMessage(MessageRole.SYSTEM,
                "【重要】请仅针对用户最新消息回复，不要重复之前已经回答过的内容。"));
        List<AgentMessage> history = messageRepository.findRecentBySessionId(
                session.getSessionId(), CONTEXT_MESSAGE_LIMIT);
        for (AgentMessage msg : history) {
            if (msg.getContentRedacted().equals(currentMessage)
                    && msg.getRole() == MessageRole.USER) {
                continue;
            }
            context.add(new ChatMessage(msg.getRole(), msg.getContentRedacted()));
        }
        context.add(new ChatMessage(MessageRole.USER, currentMessage));
        return context;
    }

    private String compressContext(AgentSession session, List<ChatMessage> context) {
        StringBuilder compressPrompt = new StringBuilder();
        compressPrompt.append("请将以下对话上下文压缩为四部分总结：\n");
        compressPrompt.append("1. 已确认事实\n2. 待确认信息\n3. 已废弃信息\n4. 最近工具结果\n\n");
        for (ChatMessage msg : context) {
            compressPrompt.append("[").append(msg.role()).append("] ")
                    .append(msg.content()).append("\n");
        }
        ChatResponse summary = languageModelPort.chat(
                "你是上下文压缩器。只输出结构化摘要，不添加额外建议。",
                List.of(), compressPrompt.toString());
        return summary.content();
    }

    private long estimateContextTokens(List<ChatMessage> context, int responseTokens) {
        long total = responseTokens;
        for (ChatMessage msg : context) {
            total += estimateTokens(msg.content());
        }
        return total;
    }

    static int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                chineseChars++;
            } else if (!Character.isWhitespace(c)) {
                otherChars++;
            }
        }
        return (int) (chineseChars * 0.5 + otherChars * 0.25);
    }

    private boolean shouldExecuteTools(ChatResponse llmResponse) {
        return !llmResponse.clarificationNeeded()
                && llmResponse.intent() != IntentType.UNKNOWN;
    }

    private Map<String, Object> extractParams(
            IntentToolMapping.ToolMapping mapping,
            Map<String, Object> cumulativeParams, String userId
    ) {
        Map<String, Object> params = new java.util.HashMap<>();
        for (var entry : mapping.paramSources().entrySet()) {
            String paramName = entry.getKey();
            IntentToolMapping.ParamSource source = entry.getValue();
            switch (source) {
                case SLOTS, SLOTS_OPTIONAL, PREVIOUS_RESULT -> {
                    Object value = cumulativeParams.get(paramName);
                    if (value != null) params.put(paramName, value);
                }
                case CONSTANT -> { /* 默认值由 ToolRouter 处理 */ }
            }
        }
        params.put("idempotencyKey",
                userId + "-" + mapping.toolName() + "-" + System.currentTimeMillis());
        return params;
    }

    private String formatToolResultsAsSystemMessage(List<ToolExecution> results) {
        StringBuilder sb = new StringBuilder("【工具调用结果】\n");
        for (ToolExecution te : results) {
            sb.append("工具: ").append(te.toolName());
            if (te.toolResult() != null) {
                sb.append(" | 状态: ").append(te.toolResult().resultCode());
                if (te.toolResult().data() != null && !te.toolResult().data().isEmpty()) {
                    sb.append(" | 数据: ").append(te.toolResult().data());
                }
            } else {
                sb.append(" | 状态: SKIPPED (").append(te.skipReason()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 从 LLM 槽位中推导澄清选项。
     */
    private List<StreamCallback.ClarificationOption> deriveClarificationOptions(
            Map<String, Object> slots) {
        if (slots == null || slots.isEmpty()) return List.of();
        for (Object value : slots.values()) {
            if (!(value instanceof List<?>) || ((List<?>) value).isEmpty()) continue;
            List<?> rawList = (List<?>) value;
            List<StreamCallback.ClarificationOption> options = new ArrayList<>();
            for (Object item : rawList.subList(0, Math.min(4, rawList.size()))) {
                if (item instanceof String s && !s.isBlank()) {
                    options.add(new StreamCallback.ClarificationOption(s, s));
                } else if (item instanceof Map<?, ?> map) {
                    Object label = map.get("label");
                    if (label == null) label = map.get("nickname");
                    if (label == null) label = map.get("name");
                    if (label instanceof String ls && !ls.isBlank()) {
                        Object id = map.get("id");
                        options.add(new StreamCallback.ClarificationOption(
                                id != null ? id.toString() : ls, ls));
                    }
                }
            }
            if (!options.isEmpty()) return options;
        }
        return List.of();
    }

    /**
     * GAP-5：检测 search_payees 返回结果中是否存在多个重名收款人。
     * 当多个用户昵称相同时返回澄清选项列表，否则返回空列表。
     */
    private List<StreamCallback.ClarificationOption> findDuplicatePayees(
            Map<String, Object> toolData) {
        Object usersObj = toolData.get("users");
        if (!(usersObj instanceof java.util.List<?> usersList) || usersList.size() < 2) {
            return List.of();
        }
        // 按昵称分组
        Map<String, List<Map<String, Object>>> byNickname = new java.util.LinkedHashMap<>();
        for (Object item : usersList) {
            if (!(item instanceof Map<?, ?> map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> userMap = (Map<String, Object>) map;
            Object nickname = userMap.get("nickname");
            if (nickname instanceof String name && !name.isBlank()) {
                byNickname.computeIfAbsent(name, k -> new ArrayList<>()).add(userMap);
            }
        }
        // 找到出现次数 > 1 的昵称，生成澄清选项
        List<StreamCallback.ClarificationOption> options = new ArrayList<>();
        for (var entry : byNickname.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (Map<String, Object> user : entry.getValue()) {
                    Object userId = user.get("userId");
                    Object phoneTail = user.get("phoneTail");
                    String label = entry.getKey()
                            + (phoneTail != null ? "（尾号" + phoneTail + "）" : "");
                    options.add(new StreamCallback.ClarificationOption(
                            userId != null ? userId.toString() : entry.getKey(), label));
                }
                break; // 只处理第一组重名
            }
        }
        return options;
    }

    // ---- 辅助方法 ----

    private void safeEmitError(StreamCallback callback, String code, String message) {
        try {
            callback.onError(code, message);
        } catch (Exception ignored) {
            // 回调异常不影响主流程
        }
    }

    private static String generateUlid() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }

    private void cleanUpLock(String sessionId) {
        ReentrantLock lock = sessionLocks.get(sessionId);
        if (lock != null && !lock.isLocked() && !lock.hasQueuedThreads()) {
            sessionLocks.remove(sessionId);
        }
    }

    private static long parseDurationMinutes(String duration) {
        String trimmed = duration.trim().toLowerCase();
        if (trimmed.endsWith("ms")) return Long.parseLong(trimmed.replace("ms", "")) / 60000;
        if (trimmed.endsWith("s")) return Long.parseLong(trimmed.replace("s", "")) / 60;
        if (trimmed.endsWith("m")) return Long.parseLong(trimmed.replace("m", ""));
        if (trimmed.endsWith("h")) return Long.parseLong(trimmed.replace("h", "")) * 60;
        return Long.parseLong(trimmed);
    }

    /**
     * 将分金额格式化为可读的元字符串，仅用于展示。
     */
    private static String formatFenDisplay(long fen) {
        return String.format("%,.2f", fen / 100.0);
    }

    /**
     * 检查文本是否包含任一关键词。
     */
    private static boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) { if (text.contains(kw)) return true; }
        return false;
    }

    /**
     * 工具执行记录（内部使用）。
     */
    private record ToolExecution(
            String toolName,
            ToolResult toolResult,
            Map<String, Object> params,
            String skipReason
    ) {
        static ToolExecution completed(String toolName, ToolResult result,
                                        Map<String, Object> params) {
            return new ToolExecution(toolName, result, params, null);
        }

        static ToolExecution skipped(String toolName, String reason) {
            return new ToolExecution(toolName, null, Map.of(), reason);
        }
    }
}
