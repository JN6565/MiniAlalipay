package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.port.LanguageModelPort;
import com.minialalipay.ai.domain.agent.*;
import com.minialalipay.common.error.BusinessException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * 会话上下文构建与消息管理公共组件。
 *
 * <p>提取 {@link AgentMessageService} 和 {@link AgentStreamService} 之间的重复逻辑，
 * 包括会话解析、上下文构建、意图推导、工具结果持久化和上下文压缩。</p>
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>会话解析与超时重新激活</li>
 *   <li>对话上下文构建（摘要、槽位、偏好、历史消息）</li>
 *   <li>意图推导（基于已执行工具列表）</li>
 *   <li>操作完成推断与描述</li>
 *   <li>工具结果消息持久化（用于历史恢复时重建卡片）</li>
 *   <li>上下文压缩与 Token 估算</li>
 * </ul>
 */
public class SessionContextHelper {

    private static final Logger log = LoggerFactory.getLogger(SessionContextHelper.class);

    private final AgentSessionRepository sessionRepository;
    private final AgentMessageRepository messageRepository;
    private final LanguageModelPort languageModelPort;
    private final UserPreferenceService userPreferenceService;
    private final ObjectMapper objectMapper;
    private final int sessionTimeoutMinutes;

    public SessionContextHelper(
            AgentSessionRepository sessionRepository,
            AgentMessageRepository messageRepository,
            LanguageModelPort languageModelPort,
            UserPreferenceService userPreferenceService,
            ObjectMapper objectMapper,
            int sessionTimeoutMinutes
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.languageModelPort = languageModelPort;
        this.userPreferenceService = userPreferenceService;
        this.objectMapper = objectMapper;
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }

    /**
     * 解析或创建会话。
     *
     * <p>支持会话超时后重新激活：保留 AI 上下文，清除过期草稿槽位。</p>
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID（可空，空则创建新会话）
     * @param now 当前时间
     * @return 活跃状态的会话对象
     * @throws BusinessException 会话不存在或归属不匹配
     */
    public AgentSession resolveSession(String userId, String sessionId, Instant now) {
        if (sessionId != null && !sessionId.isBlank()) {
            AgentSession existing = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new BusinessException(AgentErrorCode.SESSION_NOT_FOUND));
            boolean wasExpiredInDb = existing.getStatus() == AgentSessionStatus.EXPIRED;
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
            if (!existing.getUserId().equals(userId)) {
                throw new BusinessException(AgentErrorCode.SESSION_NOT_FOUND);
            }
            return existing;
        }
        AgentSession newSession = new AgentSession(AiServiceUtils.generateUlid(), userId, now);
        sessionRepository.save(newSession);
        return newSession;
    }

    /**
     * 构建对话上下文消息列表。
     *
     * <p>按顺序注入：对话摘要 → 当前槽位 → 用户偏好 → 操作完成感知 → 防重复提示 → 历史消息 → 当前消息。</p>
     *
     * @param session 当前会话
     * @param currentMessage 当前用户消息
     * @return 完整上下文消息列表
     */
    public List<ChatMessage> buildContext(AgentSession session, String currentMessage) {
        List<ChatMessage> context = new ArrayList<>();
        if (session.getSummary() != null && !session.getSummary().isBlank()) {
            context.add(new ChatMessage(MessageRole.SYSTEM,
                    "【对话摘要】" + session.getSummary()));
        }
        if (!session.getSlots().isEmpty()) {
            context.add(new ChatMessage(MessageRole.SYSTEM,
                    "【当前槽位】" + session.getSlots()));
        }
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
        Object lastAction = session.getSlots().get("lastCompletedAction");
        if (lastAction != null) {
            String actionDesc = describeCompletedAction(lastAction.toString());
            context.add(new ChatMessage(MessageRole.SYSTEM,
                    "【注意】" + actionDesc + "，请勿重复执行相同操作或重复展示相同结果。"));
        }
        context.add(new ChatMessage(MessageRole.SYSTEM,
                "【重要】请仅针对用户最新消息回复。"
                + "如果用户之前的请求已经完成（如转账已完成、余额已展示），"
                + "不要重复执行相同操作或重复展示相同结果。"
                + "对于新的请求，直接执行对应操作。"));
        List<AgentMessage> history = messageRepository.findRecentBySessionId(
                session.getSessionId(), AiServiceUtils.CONTEXT_MESSAGE_LIMIT);
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

    /**
     * 根据 AgentLoop 执行的工具列表推导用户意图。
     *
     * @param executedTools 已执行的工具名列表
     * @return 推导出的意图类型
     */
    public IntentType inferIntent(List<String> executedTools) {
        if (executedTools == null || executedTools.isEmpty()) {
            return IntentType.UNKNOWN;
        }
        String firstTool = executedTools.get(0);
        return switch (firstTool) {
            case "search_payees" ->
                    executedTools.size() > 1 ? IntentType.TRANSFER : IntentType.USER_SEARCH;
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

    /**
     * 根据已执行工具列表推断用户刚完成的操作。
     *
     * @param executedTools 已执行的工具名列表
     * @return 操作名称（如 "transfer"、"repay"），无完成操作时返回 null
     */
    public String inferCompletedAction(List<String> executedTools) {
        if (executedTools == null || executedTools.isEmpty()) return null;
        if (executedTools.contains("submit_confirmed_transfer")) return "transfer";
        if (executedTools.contains("create_credit_repayment_draft")) return "repay";
        if (executedTools.contains("prepare_confirmation_card")) return "transfer_confirm_pending";
        return null;
    }

    /**
     * 将操作名称转换为可读的中文描述，用于上下文注入。
     *
     * @param action 操作名称
     * @return 中文描述
     */
    public String describeCompletedAction(String action) {
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
     * <p>每个工具结果使用唯一的 {@code client_message_id}（格式：{@code {原始ID}_tr_{工具名}_{序号}}）
     * 以避免唯一约束冲突。时间戳在 AI 回复时间基础上逐条递增 1 毫秒，保证排序在文本回复之前。</p>
     *
     * @param toolResults 工具结果摘要列表
     * @param sessionId 会话 ID
     * @param clientMessageId 原始客户端消息幂等键
     * @param baseTime AI 回复的创建时间
     */
    public void saveToolResultMessages(List<AgentLoop.ToolResultRecord> toolResults,
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

    /**
     * 判断会话是否需要触发上下文压缩。
     *
     * <p>满足以下任一条件时返回 {@code true}：
     * <ul>
     *   <li>对话总消息数超过 10 轮（{@link AiServiceUtils#COMPRESSION_ROUND_THRESHOLD}）</li>
     *   <li>上下文 Token 估算超过上限（{@link AiServiceUtils#MAX_CONTEXT_TOKENS}）</li>
     * </ul>
     *
     * @param sessionId 会话 ID
     * @param contextTokens 当前上下文 Token 估算
     * @return 需要压缩时返回 {@code true}
     */
    public boolean needsCompression(String sessionId, long contextTokens) {
        if (contextTokens > AiServiceUtils.MAX_CONTEXT_TOKENS) {
            return true;
        }
        // PRD 10.3：对话超过 10 轮时触发压缩。
        // 查询前 N+1 条消息判断是否超过阈值，避免全表扫描。
        List<AgentMessage> probe = messageRepository.findBySessionId(
                sessionId, AiServiceUtils.COMPRESSION_ROUND_THRESHOLD + 1);
        return probe.size() > AiServiceUtils.COMPRESSION_ROUND_THRESHOLD;
    }

    /**
     * 压缩对话上下文为结构化摘要。
     *
     * @param context 当前对话上下文
     * @return 四部分结构化摘要
     */
    public String compressContext(List<ChatMessage> context) {
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

    /**
     * 估算上下文总 Token 数。
     *
     * @param context 对话上下文
     * @param responseTokens LLM 响应 Token 数
     * @return 总 Token 估算
     */
    public long estimateContextTokens(List<ChatMessage> context, int responseTokens) {
        long total = responseTokens;
        for (ChatMessage msg : context) {
            total += AiServiceUtils.estimateTokens(msg.content());
        }
        return total;
    }

    /**
     * 解析会话超时配置为分钟数。
     *
     * @param duration 超时配置字符串（如 "30m"、"1h"）
     * @return 分钟数
     */
    public static long parseDurationMinutes(String duration) {
        String trimmed = duration.trim().toLowerCase();
        if (trimmed.endsWith("ms")) return Long.parseLong(trimmed.replace("ms", "")) / 60000;
        if (trimmed.endsWith("s")) return Long.parseLong(trimmed.replace("s", "")) / 60;
        if (trimmed.endsWith("m")) return Long.parseLong(trimmed.replace("m", ""));
        if (trimmed.endsWith("h")) return Long.parseLong(trimmed.replace("h", "")) * 60;
        return Long.parseLong(trimmed);
    }
}
