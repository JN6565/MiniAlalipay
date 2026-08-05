package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.domain.agent.*;
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
    private final int sessionTimeoutMinutes;

    /** 会话级锁，保证同一会话串行处理 */
    private final ConcurrentHashMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public AgentMessageService(
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository,
            LanguageModelPort languageModelPort,
            InjectionDetector injectionDetector,
            @Value("${ai.session.timeout:30m}") String sessionTimeout
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.languageModelPort = languageModelPort;
        this.injectionDetector = injectionDetector;
        this.sessionTimeoutMinutes = (int) parseDurationMinutes(sessionTimeout);
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

            // 7. 保存 AI 回复
            String assistantMessageId = generateUlid();
            AgentMessage assistantMessage = new AgentMessage(
                    assistantMessageId, session.getSessionId(), clientMessageId,
                    MessageRole.ASSISTANT, llmResponse.content(), llmResponse.tokenCount(), now);
            messageRepository.insert(assistantMessage);

            // 8. 更新会话状态
            if (llmResponse.slots() != null && !llmResponse.slots().isEmpty()) {
                session.updateSlots(llmResponse.slots());
            }

            // 9. 上下文压缩
            long totalTokens = estimateContextTokens(context, llmResponse.tokenCount());
            if (totalTokens > MAX_CONTEXT_TOKENS) {
                String summary = compressContext(session, context);
                session.updateSummary(summary);
            }

            session.touch(now);
            sessionRepository.save(session);

            return new SendMessageResult(
                    session.getSessionId(), assistantMessageId,
                    llmResponse.content(), llmResponse.intent(),
                    llmResponse.slots(), llmResponse.lowConfidence(), false
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
