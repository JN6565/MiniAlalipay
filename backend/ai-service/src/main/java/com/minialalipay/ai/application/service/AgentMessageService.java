package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.port.LanguageModelPort;
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
 *   <li>跨会话数据通过 userId + sessionId 隔离</li>
 *   <li>支付密码、确认令牌、访问令牌不进入摘要或槽位</li>
 *   <li>userId 由服务端会话派生，不信任请求体</li>
 * </ul>
 */
@Service
public class AgentMessageService {

    private static final Logger log = LoggerFactory.getLogger(AgentMessageService.class);



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
    private final boolean mockMode;
    private final int sessionTimeoutMinutes;
    private final UserPreferenceService userPreferenceService;

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
            UserPreferenceService userPreferenceService,
            @Value("${ai.session.timeout:30m}") String sessionTimeout,
            @Value("${ai.llm.mock-mode:true}") boolean mockMode,
            @Value("${ai.prompt.system:你是一只傲娇猫娘，名叫吱托芙，现在作为aialipay助手，你的职责是帮助用户完成转账、查余额、查交易、查花呗和还花呗等操作。}") String systemPrompt
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.languageModelPort = languageModelPort;
        this.injectionDetector = injectionDetector;
        this.toolRouter = toolRouter;
        this.toolPolicy = toolPolicy;
        this.toolAudit = toolAudit;
        this.resultInterpreter = resultInterpreter;
        this.userPreferenceService = userPreferenceService;
        this.sessionTimeoutMinutes = (int) parseDurationMinutes(sessionTimeout);
        this.mockMode = mockMode;
        this.systemPrompt = systemPrompt;
    }

    /**
     * 处理用户消息并返回 AI 响应。
     *
     * @param userId 用户 ID（由网关注入，不信任客户端）
     * @param clientMessageId 客户端消息幂等键
     * @param sessionId 会话 ID（可空，空则创建新会话）
     * @param rawContent 用户输入原文
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
            String messageId = AiServiceUtils.generateUlid();
            AgentMessage userMessage = new AgentMessage(
                    messageId, session.getSessionId(), clientMessageId,
                    MessageRole.USER, rawContent, AiServiceUtils.estimateTokens(rawContent), now);
            messageRepository.insert(userMessage);
            session.touch(now);

            // 6. 构建上下文并调用 LLM
            List<ChatMessage> context = buildContext(session, rawContent);
            ChatResponse llmResponse = languageModelPort.chat(
                    systemPrompt, context.subList(0, context.size() - 1),
                    context.get(context.size() - 1).content());

            // 7. 工具执行分支：明确意图且无需澄清时调用工具获取真实数据
            String finalContent;
            Map<String, Object> finalSlots;

            if (shouldExecuteTools(llmResponse)) {
                // 转账意图前置校验：逐项检查必填槽位（收款人 → 金额），并做异常金额风险提示
                if (llmResponse.intent() == IntentType.TRANSFER) {
                    // GAP-9：收款人缺失时单独追问，不混同于金额追问
                    Object queryObj = llmResponse.slots().get("query");
                    if (queryObj == null || queryObj.toString().isBlank()) {
                        log.info("转账意图收款人缺失，引导用户补充: userId={}", userId);
                        String assistId = AiServiceUtils.generateUlid();
                        String clarifyMsg = "请问您要转账给谁？请提供收款人姓名或手机号。";
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, clarifyMsg, 0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        return new SendMessageResult(
                                session.getSessionId(), assistId, clarifyMsg,
                                llmResponse.intent(), llmResponse.slots(), true, false);
                    }
                    long amountFen = 0L;
                    Object amountObj = llmResponse.slots().get("amountFen");
                    if (amountObj instanceof Number) {
                        amountFen = ((Number) amountObj).longValue();
                    }
                    if (amountFen <= 0) {
                        log.info("转账意图金额缺失或为 0，引导用户补充: userId={}", userId);
                        String assistId = AiServiceUtils.generateUlid();
                        String clarifyMsg = "请问您要转账多少金额？";
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, clarifyMsg, 0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        return new SendMessageResult(
                                session.getSessionId(), assistId, clarifyMsg,
                                llmResponse.intent(), llmResponse.slots(), true, false);
                    }
                    // GAP-3：异常大额风险提示——单笔转账 ≥ 5000 元时主动确认
                    if (amountFen >= AiServiceUtils.LARGE_AMOUNT_THRESHOLD_FEN) {
                        log.info("大额转账风险提示: userId={}, amountFen={}", userId, amountFen);
                        String assistId = AiServiceUtils.generateUlid();
                        String riskMsg = "⚠️ 大额转账提醒：本次转账金额为 "
                                + AiServiceUtils.formatFenDisplay(amountFen) + " 元，请仔细核对收款人信息。";
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, riskMsg, 0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        return new SendMessageResult(
                                session.getSessionId(), assistId, riskMsg,
                                llmResponse.intent(), llmResponse.slots(), true, false);
                    }
                }
                // 花呗还款意图前置校验：PRD 要求禁止"全部还清"，必须指定具体金额
                if (llmResponse.intent() == IntentType.CREDIT_REPAYMENT) {
                    // 安全边界：检测用户原始消息是否包含"全部还清"关键词，强制拦截
                    String lowerRaw = rawContent.toLowerCase();
                    if (AiServiceUtils.containsAny(lowerRaw, "全部还清", "全额还", "还清全部", "一次还清")) {
                        log.info("拦截全部还清请求: userId={}, rawContent={}", userId, rawContent);
                        String assistId = AiServiceUtils.generateUlid();
                        String blockMsg = "抱歉，暂不支持全部还清功能。请告诉我您想还款的具体金额（如'还200元'）。";
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, blockMsg, 0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        return new SendMessageResult(
                                session.getSessionId(), assistId, blockMsg,
                                llmResponse.intent(), llmResponse.slots(), true, false);
                    }
                    // 金额必须大于 0
                    long amountFen = 0L;
                    Object amountObj = llmResponse.slots().get("amountFen");
                    if (amountObj instanceof Number) {
                        amountFen = ((Number) amountObj).longValue();
                    }
                    if (amountFen <= 0) {
                        log.info("还款意图金额缺失或为 0，引导用户补充: userId={}", userId);
                        String assistId = AiServiceUtils.generateUlid();
                        String clarifyMsg = "请问您要还款多少金额？";
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, clarifyMsg, 0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        return new SendMessageResult(
                                session.getSessionId(), assistId, clarifyMsg,
                                llmResponse.intent(), llmResponse.slots(), true, false);
                    }
                }

                String traceId = AiServiceUtils.generateUlid();
                List<ToolExecution> toolResults = executeTools(
                        llmResponse.intent(), llmResponse.slots(), userId, session, traceId);

                // GAP-5：检查是否存在重名收款人，如果是则返回澄清结果
                if (llmResponse.intent() == IntentType.TRANSFER && !toolResults.isEmpty()) {
                    Map<String, Object> mergedData = new java.util.HashMap<>();
                    for (ToolExecution te : toolResults) {
                        if (te.toolResult() != null && te.toolResult().data() != null) {
                            mergedData.putAll(te.toolResult().data());
                        }
                    }
                    List<Map<String, Object>> dupPayees = findDuplicatePayees(mergedData);
                    if (!dupPayees.isEmpty()) {
                        StringBuilder sb = new StringBuilder("找到多个同名收款人，请选择：");
                        for (int i = 0; i < dupPayees.size(); i++) {
                            Map<String, Object> u = dupPayees.get(i);
                            sb.append("\n").append(i + 1).append(". ")
                                    .append(u.getOrDefault("nickname", ""));
                            if (u.get("phoneTail") != null) {
                                sb.append("（尾号").append(u.get("phoneTail")).append("）");
                            }
                        }
                        String clarifyMsg = sb.toString();
                        String assistId = AiServiceUtils.generateUlid();
                        messageRepository.insert(new AgentMessage(
                                assistId, session.getSessionId(), clientMessageId,
                                MessageRole.ASSISTANT, clarifyMsg, 0, now));
                        session.touch(now);
                        sessionRepository.save(session);
                        return new SendMessageResult(
                                session.getSessionId(), assistId, clarifyMsg,
                                llmResponse.intent(), mergedData, true, false);
                    }
                }

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

            // 8. 保存 AI 回复
            String assistantMessageId = AiServiceUtils.generateUlid();
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
            if (totalTokens > AiServiceUtils.MAX_CONTEXT_TOKENS) {
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

    // ---- 私有方法 ----

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
            // 校验会话归属
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
            }
            return existing;
        }
        // 创建新会话
        AgentSession newSession = new AgentSession(AiServiceUtils.generateUlid(), userId, now);
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
        // GAP-6：注入用户偏好（如最近使用的收款人）
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
        // 获取最近 N 轮消息（先倒序取最近窗口，Repository 层负责反转回正序）
        List<AgentMessage> history = messageRepository.findRecentBySessionId(
                session.getSessionId(), AiServiceUtils.CONTEXT_MESSAGE_LIMIT);
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
            total += AiServiceUtils.estimateTokens(msg.content());
        }
        return total;
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

            // GAP-5：转账流程中 search_payees 返回多个重名收款人时中止工具链
            if ("search_payees".equals(mapping.toolName())
                    && toolResult.isSuccess() && toolResult.data() != null
                    && intent == IntentType.TRANSFER) {
                List<Map<String, Object>> dupes = findDuplicatePayees(toolResult.data());
                if (!dupes.isEmpty()) {
                    // 将重名用户列表放入 cumulativeParams 供上层处理
                    cumulativeParams.put("_duplicatePayees", dupes);
                    break;
                }
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

    /**
     * GAP-5：检测 search_payees 返回结果中是否存在多个重名收款人。
     * 当多个用户昵称相同时返回重名用户列表，否则返回空列表。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> findDuplicatePayees(Map<String, Object> toolData) {
        Object usersObj = toolData.get("users");
        if (!(usersObj instanceof java.util.List<?> usersList) || usersList.size() < 2) {
            return List.of();
        }
        Map<String, List<Map<String, Object>>> byNickname = new java.util.LinkedHashMap<>();
        for (Object item : usersList) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> userMap = (Map<String, Object>) map;
            Object nickname = userMap.get("nickname");
            if (nickname instanceof String name && !name.isBlank()) {
                byNickname.computeIfAbsent(name, k -> new ArrayList<>()).add(userMap);
            }
        }
        for (var entry : byNickname.entrySet()) {
            if (entry.getValue().size() > 1) {
                return entry.getValue();
            }
        }
        return List.of();
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
