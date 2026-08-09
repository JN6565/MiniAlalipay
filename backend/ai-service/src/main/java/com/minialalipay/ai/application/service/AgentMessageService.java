package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.domain.agent.*;
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
 * 核心推理和工具调用委托给 {@link AgentLoop}（ReAct 主循环）。
 * 会话解析、上下文构建、意图推导等公共逻辑委托给 {@link SessionContextHelper}。</p>
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
    private final InjectionDetector injectionDetector;
    private final AgentLoop agentLoop;
    private final SessionContextHelper contextHelper;

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
        this.injectionDetector = injectionDetector;
        this.agentLoop = agentLoop;
        this.systemPrompt = systemPrompt;
        this.contextHelper = new SessionContextHelper(
                sessionRepository, messageRepository, languageModelPort,
                userPreferenceService, objectMapper,
                (int) SessionContextHelper.parseDurationMinutes(sessionTimeout));
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
            List<ChatMessage> context = contextHelper.buildContext(session, rawContent);
            List<ChatMessage> history = context.subList(0, context.size() - 1);

            AgentLoop.AgentContext agentContext = new AgentLoop.AgentContext(
                    userId, session.getSessionId(), rawContent,
                    history, session, systemPrompt, null);

            AgentLoop.AgentResult agentResult = agentLoop.execute(agentContext);

            String finalContent = agentResult.finalContent();
            Map<String, Object> finalSlots = agentResult.accumulatedSlots();

            // 7. 记录最近完成的操作，防止 LLM 重复提及已完成任务
            if (finalSlots == null) {
                finalSlots = new HashMap<>();
            }
            String completedAction = contextHelper.inferCompletedAction(agentResult.executedTools(), rawContent);
            if (completedAction != null) {
                finalSlots.put("lastCompletedAction", completedAction);
            }

            // 8. 推导意图（基于执行的工具列表）
            IntentType inferredIntent = contextHelper.inferIntent(agentResult.executedTools());

            // 9. 保存 AI 回复（使用当前时间确保排序在用户消息之后）
            String assistantMessageId = AiServiceUtils.generateUlid();
            AgentMessage assistantMessage = new AgentMessage(
                    assistantMessageId, session.getSessionId(), clientMessageId,
                    MessageRole.ASSISTANT, finalContent, agentResult.totalTokens(), Instant.now());
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

            return new SendMessageResult(
                    session.getSessionId(), assistantMessageId,
                    finalContent, inferredIntent,
                    finalSlots != null ? finalSlots : Map.of(), false, false
            );

        } finally {
            if (acquired) {
                lock.unlock();
            }
            // 不清理锁：cleanUpLock 存在 check-then-act 竞态条件，
            // 锁数量等于会话数量，内存开销可控。
        }
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
