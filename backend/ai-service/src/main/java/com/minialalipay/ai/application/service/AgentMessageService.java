package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.ToolAuditService;
import com.minialalipay.ai.domain.agent.*;
import com.minialalipay.ai.infrastructure.client.ToolRouter;
import com.minialalipay.common.error.BusinessException;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>负责会话生命周期、消息幂等、上下文管理。
 * 核心推理和工具调用委托给 {@link AgentLoop}（ReAct 主循环）。</p>
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
    private final AgentLoop agentLoop;
    private final int sessionTimeoutMinutes;
    private final UserPreferenceService userPreferenceService;
    private final ObjectMapper objectMapper;

    /** 会话级锁，保证同一会话串行处理 */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public AgentMessageService(
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository,
            LanguageModelPort languageModelPort,
            InjectionDetector injectionDetector,
            AgentLoop agentLoop,
            UserPreferenceService userPreferenceService,
            ObjectMapper objectMapper,
            @Value("${ai.session.timeout:30m}") String sessionTimeout,
            @Value("${ai.prompt.system:你是一只傲娇猫娘，名叫财喵，现在作为aialipay助手，你的职责是帮助用户完成转账、查余额、查交易、查花呗和还花呗等操作。重要规则：1.如果已经通过工具获取了某项数据（如余额、额度、交易记录），不要再次调用同一工具，直接基于已有结果生成回复。2.工具选择必须精确：用户问余额/多少钱→get_balance；用户问花呗额度/信用额度→get_credit_summary；用户问交易记录/流水→list_transactions；用户问花呗账单→list_credit_bills。不要混淆余额和额度。3.转账流程必须严格按顺序完成全部4步，不得中途停止生成文本：第一步调用search_payees搜索收款人（从用户消息中提取手机号或姓名作为query参数），第二步用返回的payeeId调用create_transfer_draft创建草稿，第三步调用validate_transfer_draft校验草稿，第四步调用prepare_confirmation_card生成确认卡片。每一步完成后必须立即调用下一步工具，不要在中间步骤生成文本回复。4.从用户消息中提取金额时，将元转换为分（如100元=10000分）。5.回复中禁止输出任何原始JSON数据、工具结果标记（如[TOOL_RESULT:xxx]）或resultCode等内部字段。6.提及收款人姓名时必须使用工具返回的完整姓名，不得截断或修改。7.系统不支持备注功能，回复中不得提及备注字段。}") String systemPrompt
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.languageModelPort = languageModelPort;
        this.injectionDetector = injectionDetector;
        this.agentLoop = agentLoop;
        this.userPreferenceService = userPreferenceService;
        this.objectMapper = objectMapper;
        this.sessionTimeoutMinutes = (int) parseDurationMinutes(sessionTimeout);
        this.systemPrompt = systemPrompt;
    }

    /**
     * 处理用户消息并返回 AI 响应。
     *
     * <p>使用 AgentLoop 替代原有的 LLM 意图分类 + IntentToolMapping + 硬编码工具链。
     * Agent 循环自主决定工具调用和最终回复。</p>
     *
     * @param userId 用户 ID（由网关注入，不信任客户端）
     * @param clientMessageId 客户端消息幂等键
     * @param sessionId 会话 ID（可空，空则创建新会话）
     * @param rawContent 用户输入原文（未脱敏，用于 LLM 工具调用需要真实参数）
     * @param sanitizedContent 脱敏后内容（用于数据库存储和日志，可空则与 rawContent 相同）
     * @param now 当前时间（便于测试注入）
     * @return 处理结果，含会话状态、回复和意图信息
     */
    public SendMessageResult processMessage(
            String userId, String clientMessageId,
            String sessionId, String rawContent,
            String sanitizedContent, Instant now
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

            // 5. 保存用户消息（使用脱敏后内容存储，避免敏感信息进入数据库）
            String storeContent = (sanitizedContent != null && !sanitizedContent.isBlank())
                    ? sanitizedContent : rawContent;
            String messageId = AiServiceUtils.generateUlid();
            AgentMessage userMessage = new AgentMessage(
                    messageId, session.getSessionId(), clientMessageId,
                    MessageRole.USER, storeContent, AiServiceUtils.estimateTokens(storeContent), now);
            messageRepository.insert(userMessage);
            session.touch(now);

            // 6. 构建上下文并调用 AgentLoop（使用原始内容，LLM 需要真实参数调用工具）
            List<ChatMessage> context = buildContext(session, rawContent);
            // 上下文中最后一条是用户消息，传给 AgentLoop 时不包含（AgentLoop 自行添加）
            List<ChatMessage> history = context.subList(0, context.size() - 1);

            AgentLoop.AgentContext agentContext = new AgentLoop.AgentContext(
                    userId, session.getSessionId(), rawContent,
                    history, session, systemPrompt, null);

            AgentLoop.AgentResult agentResult = agentLoop.execute(agentContext);

            String finalContent = agentResult.finalContent();
            Map<String, Object> finalSlots = agentResult.accumulatedSlots();

            // 7.5 记录最近完成的操作，防止 LLM 重复提及已完成任务
            if (finalSlots == null) {
                finalSlots = new HashMap<>();
            }
            String completedAction = inferCompletedAction(agentResult.executedTools());
            if (completedAction != null) {
                finalSlots.put("lastCompletedAction", completedAction);
            }

            // 7. 推导意图（基于执行的工具列表）
            IntentType inferredIntent = inferIntent(agentResult.executedTools());

            // 8. 保存 AI 回复（使用当前时间确保排序在用户消息之后）
            String assistantMessageId = AiServiceUtils.generateUlid();
            AgentMessage assistantMessage = new AgentMessage(
                    assistantMessageId, session.getSessionId(), clientMessageId,
                    MessageRole.ASSISTANT, finalContent, agentResult.totalTokens(), Instant.now());
            messageRepository.insert(assistantMessage);

            // 8.5 保存工具结果消息（用于历史消息恢复时重建卡片）
            saveToolResultMessages(agentResult.toolResults(), session.getSessionId(),
                    clientMessageId, assistantMessage.getCreatedAt());

            // 9. 更新会话状态
            if (finalSlots != null && !finalSlots.isEmpty()) {
                session.updateSlots(finalSlots);
            }

            // 10. 上下文压缩
            long totalTokens = estimateContextTokens(context, agentResult.totalTokens());
            if (totalTokens > AiServiceUtils.MAX_CONTEXT_TOKENS) {
                String summary = compressContext(session, context);
                session.updateSummary(summary);
            }

            session.touch(now);
            sessionRepository.save(session);

            return new SendMessageResult(
                    session.getSessionId(), assistantMessageId,
                    finalContent, inferredIntent,
                    finalSlots != null ? finalSlots : Map.of(), false, false
            );

        } finally {
            if (acquired) {
                lock.unlock();
            }
            cleanUpLock(session.getSessionId());
        }
    }

    // ---- 私有方法 ----

    /**
     * 根据 AgentLoop 执行的工具列表推导用户意图。
     */
    private IntentType inferIntent(List<String> executedTools) {
        if (executedTools == null || executedTools.isEmpty()) {
            return IntentType.UNKNOWN;
        }
        String firstTool = executedTools.get(0);
        return switch (firstTool) {
            case "search_payees" -> {
                // 区分独立搜索收款人和转账流程
                yield executedTools.size() > 1 ? IntentType.TRANSFER : IntentType.USER_SEARCH;
            }
            case "get_balance" -> IntentType.BALANCE_QUERY;
            case "list_transactions" -> IntentType.TRANSACTION_LIST;
            case "get_transaction_status" -> IntentType.TRANSACTION_STATUS;
            case "get_credit_summary" -> IntentType.CREDIT_SUMMARY;
            case "list_credit_bills" -> IntentType.CREDIT_BILL;
            case "create_credit_repayment_draft" -> IntentType.CREDIT_REPAYMENT;
            case "create_transfer_draft" -> IntentType.TRANSFER;
            default -> IntentType.UNKNOWN;
        };
    }

    private AgentSession resolveSession(String userId, String sessionId, Instant now) {
        if (sessionId != null && !sessionId.isBlank()) {
            AgentSession existing = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BusinessException(AgentErrorCode.SESSION_NOT_FOUND));
            boolean wasExpiredInDb = existing.getStatus() == AgentSessionStatus.EXPIRED;
            // 会话超时后重新激活：保留 AI 上下文，清除过期草稿槽位
            if (existing.checkExpiry(now, sessionTimeoutMinutes)) {
                log.info("会话已超时，重新激活: sessionId={}, lastActiveAt={}",
                        existing.getSessionId(), existing.getLastActiveAt());
                existing.reactivate(now);
                if (wasExpiredInDb) {
                    sessionRepository.reactivateSession(existing);
                } else {
                    sessionRepository.save(existing);
                }
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
        // 操作完成感知：如果用户刚完成了某个操作，明确告知 LLM 不要重复
        Object lastAction = session.getSlots().get("lastCompletedAction");
        if (lastAction != null) {
            String actionDesc = describeCompletedAction(lastAction.toString());
            context.add(new ChatMessage(MessageRole.SYSTEM,
                    "【注意】" + actionDesc + "，请勿重复执行相同操作或重复展示相同结果。"));
        }
        // 增强防重复提示：更具体的指令
        context.add(new ChatMessage(MessageRole.SYSTEM,
                "【重要】请仅针对用户最新消息回复。"
                + "如果用户之前的请求已经完成（如转账已完成、余额已展示），"
                + "不要重复执行相同操作或重复展示相同结果。"
                + "对于新的请求，直接执行对应操作。"));
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

    /**
     * 根据 AgentLoop 执行的工具列表推断用户刚完成的操作。
     */
    private String inferCompletedAction(List<String> executedTools) {
        if (executedTools == null || executedTools.isEmpty()) return null;
        if (executedTools.contains("submit_confirmed_transfer")) return "transfer";
        if (executedTools.contains("create_credit_repayment_draft")) return "repay";
        if (executedTools.contains("prepare_confirmation_card")) return "transfer_confirm_pending";
        return null;
    }

    /**
     * 将操作名称转换为可读的中文描述。
     */
    private String describeCompletedAction(String action) {
        return switch (action) {
            case "transfer" -> "用户刚刚完成了转账操作";
            case "repay" -> "用户刚刚完成了花呗还款操作";
            case "transfer_confirm_pending" -> "用户已确认转账待提交";
            default -> "用户刚刚完成了" + action + "操作";
        };
    }

    /**
     * 将工具执行结果保存为独立消息，用于历史对话恢复时重建卡片。
     *
     * @param toolResults 工具结果摘要列表
     * @param sessionId 会话 ID
     * @param clientMessageId 原始客户端消息幂等键
     * @param baseTime AI 回复的创建时间
     */
    private void saveToolResultMessages(List<AgentLoop.ToolResultRecord> toolResults,
                                         String sessionId, String clientMessageId,
                                         Instant baseTime) {
        if (toolResults == null || toolResults.isEmpty()) return;
        for (int i = 0; i < toolResults.size(); i++) {
            AgentLoop.ToolResultRecord tr = toolResults.get(i);
            try {
                String json = objectMapper.writeValueAsString(Map.of(
                        "tool", tr.toolName(),
                        "status", tr.status(),
                        "summary", tr.summary(),
                        "data", tr.data() != null ? tr.data() : Map.of()
                ));
                String trClientId = clientMessageId + "_tr_" + tr.toolName() + "_" + i;
                Instant trTime = baseTime.plusMillis(i + 1);
                AgentMessage trMessage = new AgentMessage(
                        AiServiceUtils.generateUlid(), sessionId, trClientId,
                        MessageRole.ASSISTANT, json, 0, trTime,
                        MessageKind.TOOL_RESULT, tr.toolName());
                messageRepository.insert(trMessage);
            } catch (Exception e) {
                log.warn("保存工具结果消息失败: tool={}, error={}", tr.toolName(), e.getMessage());
            }
        }
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
