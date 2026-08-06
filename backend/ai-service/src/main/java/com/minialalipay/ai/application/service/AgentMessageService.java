package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.application.port.SseEvent;
import com.minialalipay.ai.application.port.StreamCallback;
import com.minialalipay.ai.application.port.ToolResult;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.ToolAuditService;
import com.minialalipay.ai.domain.agent.*;
import com.minialalipay.ai.infrastructure.client.ToolRouter;
import com.minialalipay.common.error.BusinessException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AI Agent 消息处理服务——会话引擎的核心应用编排。
 *
 * <p>负责会话生命周期、消息幂等、上下文管理、意图识别与槽位填充。
 * 不直接调用 LLM（通过 {@link LanguageModelPort}），
 * 不直接操作持久化（通过各 Repository 接口）。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>同一会话串行处理，并发请求返回 AGENT_BUSY</li>
 *   <li>跨会话数据通过 principalId + sessionId 隔离</li>
 *   <li>支付密码、确认令牌、访问令牌不进入摘要或槽位</li>
 *   <li>userId 由服务端会话派生，不信任请求体</li>
 * </ul>
 */
@Service
public class AgentMessageService {

    private static final Logger log = LoggerFactory.getLogger(AgentMessageService.class);

    /** 上下文窗口：保留最近 10 轮对话（20 条消息） */
    private static final int CONTEXT_TURN_LIMIT = 10;
    private static final int CONTEXT_MESSAGE_LIMIT = CONTEXT_TURN_LIMIT * 2;

    /** 上下文压缩阈值：消息数超过此值触发压缩 */
    private static final int COMPRESSION_THRESHOLD = CONTEXT_TURN_LIMIT * 2 + 4;

    /** Token 估算：中文每字符约 0.5 token */
    private static final int MAX_CONTEXT_TOKENS = 4096;
    private static final String SYSTEM_PROMPT = """
            你是一只傲娇猫娘，名叫吱托芙，现在作为aialipay助手，你的职责是帮助用户完成转账、查余额、查交易、
            查花呗和还花呗等操作。你必须遵守以下规则：
            1. 不臆造金额、账户或交易状态
            2. 缺失必填槽位时必须生成澄清问题，不臆造默认值
            3. 意图置信度不足时展示支持范围，不执行写工具
            4. 支付密码、确认令牌和访问令牌不出现在任何回复或摘要中
            5. 收款人必须来自受控查询工具返回值
            """;

    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final LanguageModelPort languageModelPort;
    private final InjectionDetector injectionDetector;
    private final ToolRouter toolRouter;
    private final ToolPolicyService toolPolicy;
    private final ToolAuditService toolAudit;
    private final ResultInterpreter resultInterpreter;
    private final boolean mockMode;
    private final int sessionTimeoutMinutes;

    /** 会话级锁，保证同一会话串行处理 */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public AgentMessageService(
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository,
            LanguageModelPort languageModelPort,
            InjectionDetector injectionDetector,
            ToolRouter toolRouter,
            ToolPolicyService toolPolicy,
            ToolAuditService toolAudit,
            ResultInterpreter resultInterpreter,
            @Value("${ai.session.timeout:30m}") String sessionTimeout,
            @Value("${ai.llm.mock-mode:true}") boolean mockMode
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.languageModelPort = languageModelPort;
        this.injectionDetector = injectionDetector;
        this.toolRouter = toolRouter;
        this.toolPolicy = toolPolicy;
        this.toolAudit = toolAudit;
        this.resultInterpreter = resultInterpreter;
        this.sessionTimeoutMinutes = (int) parseDurationMinutes(sessionTimeout);
        this.mockMode = mockMode;
    }

    /**
     * 处理用户消息并返回 AI 响应。
     *
     * @param userId 用户 ID（由网关注入，不信任客户端）
     * @param clientMessageId 客户端消息幂等键
     * @param sessionId 会话 ID（可空，空则创建新会话）
     * @param rawContent 用户输入原文（保存前需脱敏）
     * @param now 当前时间（便于测试注入）
     * @return 处理结果，含会话状态、回复和意图信息
     */
    public SendMessageResult processMessage(
            String userId, String clientMessageId,
            String sessionId, String rawContent, Instant now
    ) {
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
                throw new BusinessException(AgentErrorCode.AGENT_BUSY);
            }
            if (!acquired) {
                throw new BusinessException(AgentErrorCode.AGENT_BUSY);
            }

            // 3. 注入检测（纵深防御，Controller 层已做一次检测）
            InjectionDetector.InjectionCheckResult injectionCheck =
                    injectionDetector.check(rawContent);
            if (!injectionCheck.safe()) {
                log.warn("提示注入在服务层被拒绝: userId={}, pattern={}",
                        userId, injectionCheck.detectedPattern());
                throw new BusinessException(AgentErrorCode.PROMPT_INJECTION_REJECTED);
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
                return new SendMessageResult(
                        session.getSessionId(), existingUser.get().getMessageId(),
                        cachedContent, IntentType.UNKNOWN, Map.of(), false, true);
            }

            // 5. 保存用户消息（脱敏内容由调用方负责）
            String messageId = generateUlid();
            AgentMessage userMessage = new AgentMessage(
                    messageId, session.getSessionId(), clientMessageId,
                    MessageRole.USER, rawContent, estimateTokens(rawContent), now);
            messageRepository.insert(userMessage);
            session.touch(now);

            // 6. 构建上下文并调用 LLM
            List<ChatMessage> context = buildContext(session, rawContent);
            ChatResponse llmResponse = languageModelPort.chat(
                    SYSTEM_PROMPT, context.subList(0, context.size() - 1),
                    context.get(context.size() - 1).content());

            // 7. 工具执行分支：明确意图且无需澄清时调用工具获取真实数据
            String finalContent;
            Map<String, Object> finalSlots;

            if (shouldExecuteTools(llmResponse)) {
                String traceId = generateUlid();
                List<ToolExecution> toolResults = executeTools(
                        llmResponse.intent(), llmResponse.slots(), userId, session, traceId);

                if (!toolResults.isEmpty()) {
                    ToolExecution primary = toolResults.get(toolResults.size() - 1);

                    // 合并工具返回数据到槽位
                    finalSlots = new java.util.HashMap<>(llmResponse.slots());
                    for (ToolExecution te : toolResults) {
                        if (te.toolResult() != null && te.toolResult().data() != null) {
                            finalSlots.putAll(te.toolResult().data());
                        }
                    }

                    // Mock 模式：ResultInterpreter 直接生成回复
                    // 真实模式：注入工具结果后做第二轮 LLM 推理
                    if (mockMode) {
                        finalContent = resultInterpreter.interpret(
                                primary.toolName(), primary.toolResult());
                    } else {
                        String toolContext = formatToolResultsAsSystemMessage(toolResults);
                        context.add(new ChatMessage(MessageRole.SYSTEM, toolContext));
                        ChatResponse secondResponse = languageModelPort.chat(
                                SYSTEM_PROMPT, context.subList(0, context.size() - 1),
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

            // 8. 保存 AI 回复
            String assistantMessageId = generateUlid();
            AgentMessage assistantMessage = new AgentMessage(
                    assistantMessageId, session.getSessionId(), clientMessageId,
                    MessageRole.ASSISTANT, finalContent, llmResponse.tokenCount(), now);
            messageRepository.insert(assistantMessage);

            // 9. 更新会话状态
            if (!finalSlots.isEmpty()) {
                session.updateSlots(finalSlots);
            }

            // 10. 上下文压缩
            long totalTokens = estimateContextTokens(context, llmResponse.tokenCount());
            if (totalTokens > MAX_CONTEXT_TOKENS) {
                String summary = compressContext(session, context);
                session.updateSummary(summary);
            }

            session.touch(now);
            sessionRepository.save(session);

            return new SendMessageResult(
                    session.getSessionId(), assistantMessageId,
                    finalContent, llmResponse.intent(),
                    finalSlots, llmResponse.clarificationNeeded(), false
            );

        } finally {
            if (acquired) {
                lock.unlock();
            }
            cleanUpLock(session.getSessionId());
        }
    }

    /**
     * 处理用户消息，通过回调发射 SSE 流式事件。
     *
     * <p>与 {@link #processMessage(String, String, String, String, Instant)} 共享核心
     * 逻辑，但在关键节点通过 {@link StreamCallback} 通知调用方。</p>
     *
     * @param callback 流式事件回调（不可为 null）
     */
    public SendMessageResult processMessageWithStream(
            String userId, String clientMessageId,
            String sessionId, String rawContent, Instant now,
            StreamCallback callback
    ) {
        Objects.requireNonNull(callback, "StreamCallback 不能为 null");

        callback.onStatus(new SseEvent.StatusPayload("INTENT", "正在理解您的意图…"));

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
                callback.onError(new SseEvent.ErrorPayload("AGENT_BUSY", "服务繁忙，请稍后重试"));
                throw new BusinessException(AgentErrorCode.AGENT_BUSY);
            }
            if (!acquired) {
                callback.onError(new SseEvent.ErrorPayload("AGENT_BUSY", "服务繁忙，请稍后重试"));
                throw new BusinessException(AgentErrorCode.AGENT_BUSY);
            }

            // 3. 注入检测
            InjectionDetector.InjectionCheckResult injectionCheck =
                    injectionDetector.check(rawContent);
            if (!injectionCheck.safe()) {
                callback.onError(new SseEvent.ErrorPayload("PROMPT_INJECTION_REJECTED", injectionCheck.reason()));
                throw new BusinessException(AgentErrorCode.PROMPT_INJECTION_REJECTED);
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
                callback.onDone(new SseEvent.DonePayload(
                        existingUser.get().getMessageId(), session.getSessionId(), "UNKNOWN"));
                callback.onContentDelta(new SseEvent.ContentPayload(cachedContent));
                return new SendMessageResult(
                        session.getSessionId(), existingUser.get().getMessageId(),
                        cachedContent, IntentType.UNKNOWN, Map.of(), false, true);
            }

            // 5. 保存用户消息
            String messageId = generateUlid();
            AgentMessage userMessage = new AgentMessage(
                    messageId, session.getSessionId(), clientMessageId,
                    MessageRole.USER, rawContent, estimateTokens(rawContent), now);
            messageRepository.insert(userMessage);
            session.touch(now);

            // 6. 构建上下文并调用 LLM
            List<ChatMessage> context = buildContext(session, rawContent);
            ChatResponse llmResponse = languageModelPort.chat(
                    SYSTEM_PROMPT, context.subList(0, context.size() - 1),
                    context.get(context.size() - 1).content());

            callback.onStatus(new SseEvent.StatusPayload("INTENT",
                    llmResponse.intent() != IntentType.UNKNOWN
                            ? "已理解您的意图: " + llmResponse.intent()
                            : "正在分析您的需求…"));

            // 7. 工具执行分支
            String finalContent;
            Map<String, Object> finalSlots;

            if (shouldExecuteTools(llmResponse)) {
                String traceId = generateUlid();
                List<ToolExecution> toolResults = executeToolsWithStream(
                        llmResponse.intent(), llmResponse.slots(),
                        userId, session, traceId, clientMessageId, callback);

                if (!toolResults.isEmpty()) {
                    ToolExecution primary = toolResults.get(toolResults.size() - 1);

                    finalSlots = new java.util.HashMap<>(llmResponse.slots());
                    for (ToolExecution te : toolResults) {
                        if (te.toolResult() != null && te.toolResult().data() != null) {
                            finalSlots.putAll(te.toolResult().data());
                        }
                    }

                    if (mockMode) {
                        finalContent = resultInterpreter.interpret(
                                primary.toolName(), primary.toolResult());
                        callback.onContentDelta(new SseEvent.ContentPayload(finalContent));
                    } else {
                        String toolContext = formatToolResultsAsSystemMessage(toolResults);
                        context.add(new ChatMessage(MessageRole.SYSTEM, toolContext));
                        callback.onStatus(new SseEvent.StatusPayload("REPLYING", "正在生成回复…"));
                        ChatResponse secondResponse = languageModelPort.chat(
                                SYSTEM_PROMPT, context.subList(0, context.size() - 1),
                                "请基于工具调用结果回复用户，使用自然语言。");
                        finalContent = secondResponse.content();
                        // 逐句推送
                        for (String sentence : finalContent.split("(?<=[。！？\\n])")) {
                            if (!sentence.isBlank()) {
                                callback.onContentDelta(new SseEvent.ContentPayload(sentence.trim()));
                            }
                        }
                    }

                    // 检查是否需要确认卡片（转账草稿链的最后一步成功时）
                    if (primary.toolName().equals("prepare_confirmation_card")
                            && primary.toolResult() != null && primary.toolResult().isSuccess()) {
                        Map<String, Object> data = primary.toolResult().data();
                        callback.onConfirmation(new SseEvent.ConfirmationPayload(
                                "transfer",
                                (String) data.getOrDefault("draftId", ""),
                                (String) data.getOrDefault("payeeNickname", ""),
                                ((Number) data.getOrDefault("amountFen", 0L)).longValue(),
                                (String) data.getOrDefault("payeePhoneTail", ""),
                                "向" + data.getOrDefault("payeeNickname", "收款人")
                                        + "转账 " + ResultInterpreter.formatFen(data.get("amountFen")) + " 元"
                        ));
                    }
                } else {
                    finalContent = llmResponse.content();
                    finalSlots = llmResponse.slots();
                    if (!finalContent.isBlank()) {
                        callback.onContentDelta(new SseEvent.ContentPayload(finalContent));
                    }
                }
            } else {
                finalContent = llmResponse.content();
                finalSlots = llmResponse.slots();
                if (llmResponse.clarificationNeeded()) {
                    callback.onClarification(new SseEvent.ClarificationPayload(
                            finalContent, List.of()));
                } else if (!finalContent.isBlank()) {
                    callback.onContentDelta(new SseEvent.ContentPayload(finalContent));
                }
            }

            // 8. 保存 AI 回复
            String assistantMessageId = generateUlid();
            AgentMessage assistantMessage = new AgentMessage(
                    assistantMessageId, session.getSessionId(), clientMessageId,
                    MessageRole.ASSISTANT, finalContent, llmResponse.tokenCount(), now);
            messageRepository.insert(assistantMessage);

            // 9. 更新会话状态
            if (!finalSlots.isEmpty()) {
                session.updateSlots(finalSlots);
            }

            // 10. 上下文压缩
            long totalTokens = estimateContextTokens(context, llmResponse.tokenCount());
            if (totalTokens > MAX_CONTEXT_TOKENS) {
                String summary = compressContext(session, context);
                session.updateSummary(summary);
            }

            session.touch(now);
            sessionRepository.save(session);

            callback.onDone(new SseEvent.DonePayload(
                    assistantMessageId, session.getSessionId(), llmResponse.intent().name()));

            return new SendMessageResult(
                    session.getSessionId(), assistantMessageId,
                    finalContent, llmResponse.intent(),
                    finalSlots, llmResponse.clarificationNeeded(), false);

        } finally {
            if (acquired) {
                lock.unlock();
            }
            cleanUpLock(session.getSessionId());
        }
    }

    // ---- 私有方法 ----

    private AgentSession resolveSession(String userId, String sessionId, Instant now) {
        if (sessionId != null && !sessionId.isBlank()) {
            AgentSession existing = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BusinessException(AgentErrorCode.SESSION_NOT_FOUND));
            if (!existing.isActive()) {
                throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
            }
            // 校验会话归属
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
            }
            return existing;
        }
        // 创建新会话
        AgentSession newSession = new AgentSession(generateUlid(), userId, now);
        sessionRepository.save(newSession);
        return newSession;
    }

    private List<ChatMessage> buildContext(AgentSession session, String currentMessage) {
        List<ChatMessage> context = new ArrayList<>();
        // 系统注入：摘要和当前槽位
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            context.add(new ChatMessage(MessageRole.SYSTEM,
                    "【对话摘要】" + session.getSummary()));
        }
        if (!session.getSlots().isEmpty()) {
            context.add(new ChatMessage(MessageRole.SYSTEM,
                    "【当前槽位】" + session.getSlots()));
        }
        // 获取最近 N 轮消息（先倒序取最近窗口，Repository 层负责反转回正序）
        List<AgentMessage> history = messageRepository.findRecentBySessionId(
                session.getSessionId(), CONTEXT_MESSAGE_LIMIT);
        for (AgentMessage msg : history) {
            // 跳过刚插入的当前消息（避免重复）
            if (msg.getContentRedacted().equals(currentMessage)
                    && msg.getRole() == MessageRole.USER) {
                continue;
            }
            context.add(new ChatMessage(msg.getRole(), msg.getContentRedacted()));
        }
        // 当前用户消息
        context.add(new ChatMessage(MessageRole.USER, currentMessage));
        return context;
    }

    private String compressContext(AgentSession session, List<ChatMessage> context) {
        // 构建压缩提示：要求 LLM 总结为四部分
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
        // 中文字符约 0.5 token/字，英文字符约 0.25 token/字
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

    private static String generateUlid() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }

    private void cleanUpLock(String sessionId) {
        ReentrantLock lock = sessionLocks.get(sessionId);
        if (lock != null && !lock.isLocked() && !lock.hasQueuedThreads()) {
            sessionLocks.remove(sessionId);
        }
    }

    /**
     * 判断是否需要执行工具：意图明确且 LLM 不需要进一步澄清。
     */
    private boolean shouldExecuteTools(ChatResponse llmResponse) {
        return !llmResponse.clarificationNeeded()
                && llmResponse.intent() != IntentType.UNKNOWN;
    }

    /**
     * 按意图→工具映射表执行有序工具调用链。
     *
     * <p>链式依赖的工具在前驱失败时自动跳过；权限拒绝的工具记录原因后跳过。</p>
     */
    private List<ToolExecution> executeTools(
            IntentType intent, Map<String, Object> slots,
            String userId, AgentSession session, String traceId
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

            Map<String, Object> params = extractParams(mapping, cumulativeParams, userId);

            long startMs = System.currentTimeMillis();
            ToolResult toolResult = toolRouter.route(mapping.toolName(), params, userId);
            int duration = (int) (System.currentTimeMillis() - startMs);

            toolAudit.audit(mapping.toolName(), params, toolResult.resultCode(),
                    session.getSessionId(), traceId, duration, Instant.now());

            results.add(ToolExecution.completed(mapping.toolName(), toolResult, params));

            if (!toolResult.isSuccess()) {
                log.warn("工具执行失败，中止链条: tool={}, resultCode={}",
                        mapping.toolName(), toolResult.resultCode());
                break;
            }

            if (toolResult.data() != null && !toolResult.data().isEmpty()) {
                cumulativeParams.putAll(toolResult.data());
            }
        }
        return results;
    }

    /**
     * 带 SSE 回调的工具执行链，在工具调用前后发射事件。
     */
    private List<ToolExecution> executeToolsWithStream(
            IntentType intent, Map<String, Object> slots,
            String userId, AgentSession session, String traceId,
            String clientMessageId, StreamCallback callback
    ) {
        List<IntentToolMapping.ToolMapping> mappings =
                IntentToolMapping.getToolsForIntent(intent);
        if (mappings.isEmpty()) {
            return List.of();
        }

        callback.onStatus(new SseEvent.StatusPayload("EXECUTING", "正在执行操作…"));

        List<ToolExecution> results = new java.util.ArrayList<>();
        Map<String, Object> cumulativeParams = new java.util.HashMap<>(slots);

        for (IntentToolMapping.ToolMapping mapping : mappings) {
            ToolPolicyService.PolicyDecision decision =
                    toolPolicy.evaluate(mapping.toolName(), session);
            if (!decision.allowed()) {
                log.warn("工具执行被策略拒绝: tool={}, reason={}",
                        mapping.toolName(), decision.reason());
                callback.onToolCall(new SseEvent.ToolCallPayload(
                        mapping.toolName(), "rejected"));
                results.add(ToolExecution.skipped(mapping.toolName(), decision.reason()));
                if (mapping.isChainDependent()) break;
                continue;
            }

            callback.onToolCall(new SseEvent.ToolCallPayload(
                    mapping.toolName(), "running"));

            Map<String, Object> params = extractParams(mapping, cumulativeParams, userId, clientMessageId);

            long startMs = System.currentTimeMillis();
            ToolResult toolResult = toolRouter.route(mapping.toolName(), params, userId);
            int duration = (int) (System.currentTimeMillis() - startMs);

            toolAudit.audit(mapping.toolName(), params, toolResult.resultCode(),
                    session.getSessionId(), traceId, duration, Instant.now());

            String status = toolResult.isSuccess() ? "success" : "failed";
            String summary = toolResult.isSuccess() && toolResult.data() != null
                    ? "共获取 " + toolResult.data().size() + " 项数据"
                    : toolResult.errorMessage();
            callback.onToolResult(new SseEvent.ToolResultPayload(
                    mapping.toolName(), status, summary != null ? summary : ""));

            results.add(ToolExecution.completed(mapping.toolName(), toolResult, params));

            if (!toolResult.isSuccess()) {
                log.warn("工具执行失败，中止链条: tool={}, resultCode={}",
                        mapping.toolName(), toolResult.resultCode());
                break;
            }

            if (toolResult.data() != null && !toolResult.data().isEmpty()) {
                cumulativeParams.putAll(toolResult.data());
            }
        }
        return results;
    }

    /**
     * 根据映射定义的参数来源，从槽位或前驱结果中提取工具调用参数。
     */
    private Map<String, Object> extractParams(
            IntentToolMapping.ToolMapping mapping,
            Map<String, Object> cumulativeParams, String userId
    ) {
        Map<String, Object> params = new java.util.HashMap<>();
        for (var entry : mapping.paramSources().entrySet()) {
            String paramName = entry.getKey();
            IntentToolMapping.ParamSource source = entry.getValue();
            switch (source) {
                case SLOTS -> {
                    Object value = cumulativeParams.get(paramName);
                    if (value != null) {
                        params.put(paramName, value);
                    }
                }
                case SLOTS_OPTIONAL -> {
                    Object value = cumulativeParams.get(paramName);
                    if (value != null) {
                        params.put(paramName, value);
                    }
                }
                case PREVIOUS_RESULT -> {
                    Object value = cumulativeParams.get(paramName);
                    if (value != null) {
                        params.put(paramName, value);
                    }
                }
                case CONSTANT -> {
                    // 默认值由 ToolRouter.dispatch() 中的 getOrDefault 处理
                }
            }
        }
        params.put("idempotencyKey",
                userId + "-" + mapping.toolName() + "-" + System.currentTimeMillis());
        return params;
    }

    /**
     * 根据映射定义的参数来源，从槽位或前驱结果中提取工具调用参数。
     * 支持客户端消息 ID 用于幂等键生成。
     */
    private Map<String, Object> extractParams(
            IntentToolMapping.ToolMapping mapping,
            Map<String, Object> cumulativeParams, String userId,
            String clientMessageId
    ) {
        Map<String, Object> params = new java.util.HashMap<>();
        for (var entry : mapping.paramSources().entrySet()) {
            String paramName = entry.getKey();
            IntentToolMapping.ParamSource source = entry.getValue();
            switch (source) {
                case SLOTS -> {
                    Object value = cumulativeParams.get(paramName);
                    if (value != null) {
                        params.put(paramName, value);
                    }
                }
                case SLOTS_OPTIONAL -> {
                    Object value = cumulativeParams.get(paramName);
                    if (value != null) {
                        params.put(paramName, value);
                    }
                }
                case PREVIOUS_RESULT -> {
                    Object value = cumulativeParams.get(paramName);
                    if (value != null) {
                        params.put(paramName, value);
                    }
                }
                case CONSTANT -> {
                    // 默认值由 ToolRouter.dispatch() 中的 getOrDefault 处理
                }
            }
        }
        params.put("idempotencyKey",
                userId + "-" + mapping.toolName() + "-" + clientMessageId);
        return params;
    }

    /**
     * 将工具执行结果格式化为 System Message 注入上下文。
     * 仅在真实 LLM 模式下使用。
     */
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
     * 单次工具执行记录（内部使用）。
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

    private static long parseDurationMinutes(String duration) {
        String trimmed = duration.trim().toLowerCase();
        if (trimmed.endsWith("ms")) return Long.parseLong(trimmed.replace("ms", "")) / 60000;
        if (trimmed.endsWith("s")) return Long.parseLong(trimmed.replace("s", "")) / 60;
        if (trimmed.endsWith("m")) return Long.parseLong(trimmed.replace("m", ""));
        if (trimmed.endsWith("h")) return Long.parseLong(trimmed.replace("h", "")) * 60;
        return Long.parseLong(trimmed);
    }

    // ================================================================
    // 结果记录
    // ================================================================

    /**
     * 消息处理结果，供 Controller 层转换为 API 响应。
     */
    public record SendMessageResult(
            String sessionId,
            String messageId,
            String content,
            IntentType intent,
            Map<String, Object> slots,
            boolean clarificationNeeded,
            boolean fromCache
    ) {
    }
}
