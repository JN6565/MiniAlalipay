package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.application.port.*;
import com.minialalipay.ai.application.security.InjectionDetector;
import com.minialalipay.ai.application.security.ToolAuditService;
import com.minialalipay.ai.domain.agent.AgentSession;
import com.minialalipay.ai.domain.agent.MessageRole;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.ai.domain.tool.ToolRiskLevel;
import com.minialalipay.ai.infrastructure.client.ToolRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/**
 * Agent ReAct 主循环编排器。
 *
 * <p>实现感知-推理-行动（Reasoning + Acting）循环：
 * <ol>
 *   <li>构建消息列表（system prompt + 历史 + 用户消息）</li>
 *   <li>调用 LLM agentStep 获取决策</li>
 *   <li>若为工具调用：安全检查 → 执行 → 审计 → 结果追加 → 回到步骤 2</li>
 *   <li>若为最终回复：结束循环</li>
 *   <li>超过最大迭代次数：强制生成总结回复</li>
 * </ol>
 *
 * <p>安全机制保留：每次工具调用前执行 ToolPolicy 评估和 InjectionDetector 参数检查，
 * 调用后执行 ToolAudit 审计。HIGH_RISK_WRITE 工具不在 Agent 可用工具列表中。</p>
 */
@Service
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);
    private static final int MAX_ITERATIONS = 6;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final LanguageModelPort languageModelPort;
    private final ToolCatalog toolCatalog;
    private final ToolRouter toolRouter;
    private final ToolPolicyService toolPolicy;
    private final ToolAuditService toolAudit;
    private final InjectionDetector injectionDetector;
    private final ResultInterpreter resultInterpreter;
    private final TransferFlowAutoAdvance flowAutoAdvance;

    public AgentLoop(
            LanguageModelPort languageModelPort,
            ToolCatalog toolCatalog,
            ToolRouter toolRouter,
            ToolPolicyService toolPolicy,
            ToolAuditService toolAudit,
            InjectionDetector injectionDetector,
            ResultInterpreter resultInterpreter
    ) {
        this.languageModelPort = languageModelPort;
        this.toolCatalog = toolCatalog;
        this.toolRouter = toolRouter;
        this.toolPolicy = toolPolicy;
        this.toolAudit = toolAudit;
        this.injectionDetector = injectionDetector;
        this.resultInterpreter = resultInterpreter;
        this.flowAutoAdvance = new TransferFlowAutoAdvance(resultInterpreter);
    }

    /**
     * Agent 执行上下文。
     *
     * @param userId 用户 ID
     * @param sessionId 会话 ID
     * @param userMessage 用户原始消息
     * @param history 对话历史（ChatMessage 列表）
     * @param session 会话对象（用于策略评估）
     * @param systemPrompt 系统提示词
     * @param callback SSE 回调（可空，同步模式不需要）
     */
    public record AgentContext(
            String userId,
            String sessionId,
            String userMessage,
            List<ChatMessage> history,
            AgentSession session,
            String systemPrompt,
            StreamCallback callback
    ) {}

    /**
     * Agent 执行结果。
     *
     * @param finalContent 最终回复内容
     * @param executedTools 执行过的工具名列表（用于推导 intent）
     * @param accumulatedSlots 累积的工具返回数据
     * @param totalTokens 总 Token 消耗估算
     * @param iterationCount 实际迭代次数
     * @param pendingText LLM 在工具调用前生成的过渡文本（需先于最终内容推送）
     * @param toolResults 各工具执行结果的摘要记录（用于持久化到历史消息）
     */
    public record AgentResult(
            String finalContent,
            List<String> executedTools,
            Map<String, Object> accumulatedSlots,
            int totalTokens,
            int iterationCount,
            String pendingText,
            List<ToolResultRecord> toolResults
    ) {
        /** 兼容构造（无 pendingText 和 toolResults） */
        public AgentResult(String finalContent, List<String> executedTools,
                           Map<String, Object> accumulatedSlots, int totalTokens, int iterationCount) {
            this(finalContent, executedTools, accumulatedSlots, totalTokens, iterationCount, null, List.of());
        }

        /** 兼容构造（无 toolResults） */
        public AgentResult(String finalContent, List<String> executedTools,
                           Map<String, Object> accumulatedSlots, int totalTokens, int iterationCount,
                           String pendingText) {
            this(finalContent, executedTools, accumulatedSlots, totalTokens, iterationCount,
                    pendingText, List.of());
        }
    }

    /**
     * 工具执行结果摘要，用于持久化到历史消息。
     *
     * @param toolName 工具名称
     * @param status 执行状态：success / failed
     * @param summary 自然语言摘要
     * @param data 结构化数据（可为空 Map）
     */
    public record ToolResultRecord(
            String toolName,
            String status,
            String summary,
            Map<String, Object> data
    ) {}

    /**
     * 同步执行 Agent 循环。
     *
     * @param context 执行上下文
     * @return 执行结果
     */
    public AgentResult execute(AgentContext context) {
        List<ChatMessage> messages = buildInitialMessages(context);
        List<ToolCatalog.ToolDefinition> availableTools = getAvailableTools();
        List<String> executedTools = new ArrayList<>();
        Map<String, Object> accumulatedSlots = new HashMap<>();
        int totalTokens = 0;
        int iteration = 0;

        // 工具调用去重：防止 LLM 在 ReAct 循环中重复调用同一工具（同工具名+同参数）。
        // 重复调用会导致前端渲染多个相同的工具结果卡片，浪费后端资源。
        Set<String> executedToolKeys = new HashSet<>();
        Map<String, ToolResult> previousToolResults = new HashMap<>();

        // 累积 LLM 在工具调用前生成的过渡文本（如“好的，我来为您...”）。
        // 这些文本需要在前端先于最终内容显示，否则用户看不到 AI 的过渡回复。
        StringBuilder pendingTextBuilder = new StringBuilder();

        // 收集各工具执行结果的摘要，用于持久化到历史消息。
        List<ToolResultRecord> toolResults = new ArrayList<>();

        for (iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            // 调用 LLM agentStep
            AgentDecision decision = languageModelPort.agentStep(messages, availableTools);
            totalTokens += decision.estimatedTokens();

            if (decision instanceof AgentDecision.FinalReply finalReply) {
                // 文本工具调用解析：LLM 有时会以文本形式输出工具调用（如 [TOOL_CALL:toolName]{args}）
                // 而非通过函数调用机制。此时需要解析并执行，否则流程会中断。
                AgentDecision.ToolCall parsedToolCall = parseToolCallFromText(finalReply.content());
                if (parsedToolCall != null) {
                    log.info("从 FinalReply 文本中解析到工具调用: tool={}, args={}",
                            parsedToolCall.toolName(), parsedToolCall.arguments());

                    // 提取工具调用之前的文本内容（LLM 的过渡性回复），累积到 pendingText
                    String textBeforeTool = extractTextBeforeToolCall(finalReply.content());
                    if (textBeforeTool != null && !textBeforeTool.isBlank()) {
                        log.info("保留 LLM 过渡文本: {}", textBeforeTool);
                        if (pendingTextBuilder.length() > 0) pendingTextBuilder.append("\n");
                        pendingTextBuilder.append(textBeforeTool);
                        // 作为 Assistant 消息加入历史，让 LLM 在下一轮知道已经说过这些话
                        messages.add(new ChatMessage(MessageRole.ASSISTANT, textBeforeTool));
                        // 注意：不通过 callback 推送，由 AgentStreamService 在循环结束后
                        // 统一推送 pendingText + finalContent，避免重复推送导致前端文本重复。
                    }

                    // 将解析到的工具调用作为 ToolCall 处理
                    decision = parsedToolCall;
                    // 继续下面的 ToolCall 处理逻辑（不 return）
                } else {
                    // 转账流程推进：LLM 提前生成文本回复但流程未完成时，
                    // 不再仅依赖注入提示（DeepSeek 经常不遵循），改为自动执行下一步工具。
                    log.info("LLM 返回 FinalReply: executedTools={}, contentLength={}",
                            executedTools, finalReply.content() != null ? finalReply.content().length() : 0);

                    String llmText = finalReply.content();
                    String flowHint = flowAutoAdvance.detectIncompleteFlow(executedTools);

                    if (flowHint != null) {
                        log.info("检测到未完成流程，自动执行下一步工具: hint={}", flowHint);

                        if (llmText != null && !llmText.isBlank()) {
                            if (pendingTextBuilder.length() > 0) pendingTextBuilder.append("\n");
                            pendingTextBuilder.append(llmText);
                            messages.add(new ChatMessage(MessageRole.ASSISTANT, llmText));
                        }

                        boolean advanced = flowAutoAdvance.tryAutoAdvanceFromFinalReply(
                                executedTools, accumulatedSlots,
                                messages, executedToolKeys, previousToolResults, context, iteration,
                                toolResults, new AgentLoopToolHandler());
                        if (advanced) {
                            log.info("FinalReply 自动推进成功，继续循环等待最终回复");
                            continue;
                        }
                        log.warn("自动推进失败，回退到注入提示");
                        messages.add(new ChatMessage(MessageRole.SYSTEM,
                                "【流程推进】" + flowHint + "请立即调用下一步工具，不要生成文本回复。"));
                        continue;
                    }

                    // ★ 流程已完成：LLM 文本作为最终回复返回。
                    // 不添加到 pendingTextBuilder（避免 AgentStreamService 重复推送），
                    // 直接作为 finalContent 返回，由 AgentStreamService 统一推送。
                    if (llmText != null && !llmText.isBlank()) {
                        messages.add(new ChatMessage(MessageRole.ASSISTANT, llmText));
                    }

                    log.info("流程已完成或无需推进，结束循环: iteration={}", iteration);
                    // 如果有 pendingText（来自之前的自动推进累积），正常返回；
                    // 当前 LLM 文本作为 finalContent，AgentStreamService 先推 pendingText 再推 finalContent。
                    String finalContent = sanitizeFinalContent(finalReply.content());
                    return new AgentResult(
                            finalContent, executedTools, accumulatedSlots,
                            totalTokens, iteration + 1,
                            pendingTextBuilder.length() > 0 ? pendingTextBuilder.toString() : null,
                            toolResults);
                }
            }

            if (decision instanceof AgentDecision.ToolCall toolCall) {
                // 工具调用去重：
                // 只读查询类工具按工具名去重（同一工具只执行一次，避免 LLM 用不同参数重复调用）
                // 非查询类工具按工具名+参数组合去重（允许同工具不同参数的合理场景）
                String toolKey = toolCall.toolName() + ":" + serializeArgs(toolCall.arguments());
                boolean isReadOnlyQuery = isReadOnlyQueryTool(toolCall.toolName());
                String dedupKey = isReadOnlyQuery ? toolCall.toolName() : toolKey;
                if (executedToolKeys.contains(dedupKey)) {
                    log.info("跳过重复工具调用: tool={}, dedupKey={}, isReadOnly={}",
                            toolCall.toolName(), dedupKey, isReadOnlyQuery);
                    // 复用之前的工具结果，不再通知前端（避免重复卡片）
                    ToolResult previousResult = previousToolResults.get(isReadOnlyQuery ? dedupKey : toolKey);
                    // 不再重复添加 executedTools（首次执行时已添加）
                    String toolResultJson = formatToolResult(toolCall.toolName(), previousResult);

                    // ★ 转账流程自动推进：LLM 重复调用中间步骤时，
                    //   自动执行下一步而非仅注入提示。
                    if (flowAutoAdvance.tryAutoAdvanceTransferFlow(
                            toolCall.toolName(), executedTools, accumulatedSlots,
                            messages, executedToolKeys, previousToolResults, context, iteration,
                            toolResults, new AgentLoopToolHandler())) {
                        log.info("转账流程自动推进成功，跳过 LLM 重复调用");
                        // 自动推进已完成（prepare_confirmation_card 已执行），
                        // 注入简短提示让 LLM 生成最终回复
                        messages.add(new ChatMessage(MessageRole.SYSTEM,
                                "确认卡片已生成。请直接生成最终回复，向用户展示确认信息，不要再调用任何工具。"));
                        continue;
                    }

                    // 非转账流程或无法自动推进时，注入纠正提示
                    String flowHint = flowAutoAdvance.detectIncompleteFlow(executedTools);
                    if (flowHint != null) {
                        messages.add(new ChatMessage(MessageRole.SYSTEM,
                                "【重复调用纠正】" + toolCall.toolName() + " 已经调用过了，不要重复调用。" + flowHint));
                    } else {
                        messages.add(new ChatMessage(MessageRole.SYSTEM,
                                "【重复调用纠正】" + toolCall.toolName() + " 已经调用过了，结果如下：" + toolResultJson + "\n请基于以上结果生成最终回复，或调用其他工具。"));
                    }
                    continue;
                }

                // 工具选择校验：检测 LLM 是否选错了工具（如用户问花呗却选了余额查询）
                // 校验不通过时注入纠正提示，让 LLM 在下一轮自行修正，不浪费工具调用
                String mismatchHint = detectToolMismatch(toolCall.toolName(), context.userMessage());
                if (mismatchHint != null) {
                    log.warn("工具选择与用户意图不匹配: tool={}, userMessage={}, hint={}",
                            toolCall.toolName(), context.userMessage(), mismatchHint);
                    messages.add(new ChatMessage(MessageRole.SYSTEM,
                            "【纠正】" + mismatchHint + "请重新选择正确的工具。"));
                    continue;
                }

                executedToolKeys.add(dedupKey);

                // 执行工具
                ToolExecutionResult result = executeToolCall(toolCall, context, accumulatedSlots);
                previousToolResults.put(dedupKey, result.toolResult());

                // 通知回调
                String toolStatus = result.toolResult().isSuccess() ? "success" : "failed";
                String toolSummary = result.toolResult().isSuccess()
                        ? resultInterpreter.interpret(toolCall.toolName(), result.toolResult())
                        : (result.toolResult().errorMessage() != null
                            ? result.toolResult().errorMessage() : "工具执行失败");
                Map<String, Object> toolResultData = result.toolResult().data() != null
                        ? new HashMap<>(result.toolResult().data()) : Map.of();
                if (context.callback() != null) {
                    context.callback().onToolCall(toolCall.toolName(), "running");
                    context.callback().onToolResult(toolCall.toolName(), toolStatus, toolSummary, toolResultData);
                }
                // 记录工具结果摘要，用于持久化到历史消息
                toolResults.add(new ToolResultRecord(toolCall.toolName(), toolStatus, toolSummary, toolResultData));

                executedTools.add(toolCall.toolName());
                if (result.toolResult().data() != null) {
                    accumulatedSlots.putAll(result.toolResult().data());
                }
                log.info("工具执行成功: tool={}, executedTools={}, iteration={}",
                        toolCall.toolName(), executedTools, iteration);

                // 将工具结果追加到消息列表（SystemMessage 方式）
                String toolResultJson = formatToolResult(toolCall.toolName(), result.toolResult());
                messages.add(new ChatMessage(MessageRole.SYSTEM, toolResultJson));

                // 工具执行失败时不再继续循环
                if (!result.toolResult().isSuccess()) {
                    log.warn("工具执行失败，终止循环: tool={}, resultCode={}",
                            toolCall.toolName(), result.toolResult().resultCode());
                    String errorContent = result.toolResult().errorMessage() != null
                            ? result.toolResult().errorMessage()
                            : resultInterpreter.interpret(toolCall.toolName(), result.toolResult());
                    return new AgentResult(errorContent, executedTools, accumulatedSlots,
                            totalTokens, iteration + 1,
                            pendingTextBuilder.length() > 0 ? pendingTextBuilder.toString() : null,
                            toolResults);
                }
            }
        }

        // 达到最大迭代次数，强制生成总结回复
        log.warn("Agent 达到最大迭代次数 {}，强制结束", MAX_ITERATIONS);
        AgentDecision finalDecision = languageModelPort.agentStep(messages, List.of());
        totalTokens += finalDecision.estimatedTokens();
        String forceContent = sanitizeFinalContent((finalDecision instanceof AgentDecision.FinalReply fr)
                ? fr.content()
                : "操作处理中，请稍后查看结果。");
        return new AgentResult(forceContent, executedTools, accumulatedSlots,
                totalTokens, MAX_ITERATIONS,
                pendingTextBuilder.length() > 0 ? pendingTextBuilder.toString() : null,
                toolResults);
    }

    /**
     * 流式执行 Agent 循环。
     *
     * <p>与同步执行逻辑相同，但最终回复通过流式 LLM 调用生成。
     * 工具调用阶段通过 StreamCallback 发射事件。</p>
     *
     * @param context 执行上下文（callback 和 onContentDelta 不可空）
     * @return 执行结果
     */
    public AgentResult executeStreaming(AgentContext context) {
        // 先执行同步循环获取最终内容和工具结果
        AgentResult syncResult = execute(context);

        // 如果有流式回调且内容非空，通过回调推送内容
        if (context.callback() != null && syncResult.finalContent() != null
                && !syncResult.finalContent().isBlank()) {
            // 内容已通过 onToolResult 回调推送了工具结果
            // 最终回复通过 onContentDelta 推送
            // 注意：这里不做分块模拟流式，由调用方（AgentStreamService）决定如何推送
        }

        return syncResult;
    }

    // ---- 内部方法 ----

    /**
     * 构建初始消息列表：system prompt + 历史上下文 + 用户消息。
     */
    private List<ChatMessage> buildInitialMessages(AgentContext context) {
        List<ChatMessage> messages = new ArrayList<>();
        // 系统提示词
        messages.add(new ChatMessage(MessageRole.SYSTEM, context.systemPrompt()));
        // 历史上下文
        if (context.history() != null) {
            messages.addAll(context.history());
        }
        // 当前用户消息
        messages.add(new ChatMessage(MessageRole.USER, context.userMessage()));
        return messages;
    }

    /**
     * 获取 Agent 可用工具列表（排除 HIGH_RISK_WRITE）。
     */
    private List<ToolCatalog.ToolDefinition> getAvailableTools() {
        return toolCatalog.allTools().values().stream()
                .filter(t -> t.riskLevel() != ToolRiskLevel.HIGH_RISK_WRITE)
                .toList();
    }

    /**
     * 执行单个工具调用，包含安全检查和审计。
     */
    private ToolExecutionResult executeToolCall(
            AgentDecision.ToolCall toolCall,
            AgentContext context,
            Map<String, Object> accumulatedSlots
    ) {
        String toolName = toolCall.toolName();
        Map<String, Object> arguments = toolCall.arguments();

        // 1. 工具策略评估
        ToolPolicyService.PolicyDecision policyDecision =
                toolPolicy.evaluate(toolName, context.session());
        if (!policyDecision.allowed()) {
            log.warn("工具被策略拒绝: tool={}, reason={}", toolName, policyDecision.reason());
            return new ToolExecutionResult(
                    new ToolResult("POLICY_DENIED", Map.of(), policyDecision.reason(), 0));
        }

        // 2. 参数注入检测
        String argsJson = serializeArgs(arguments);
        if (!argsJson.isBlank()) {
            InjectionDetector.InjectionCheckResult injectionCheck =
                    injectionDetector.check(argsJson);
            if (!injectionCheck.safe()) {
                log.warn("工具参数注入检测拒绝: tool={}, pattern={}",
                        toolName, injectionCheck.detectedPattern());
                return new ToolExecutionResult(
                        new ToolResult("INJECTION_DETECTED", Map.of(),
                                "工具参数包含不安全内容", 0));
            }
        }

        // 3. 构建调用参数（添加幂等键）
        Map<String, Object> params = new HashMap<>(arguments);
        params.put("idempotencyKey",
                context.userId() + "-" + toolName + "-" + System.currentTimeMillis());

        // 4. 执行工具
        long startMs = System.currentTimeMillis();
        ToolResult toolResult;
        try {
            toolResult = toolRouter.route(toolName, params, context.userId());
        } catch (Exception e) {
            log.error("工具执行异常: tool={}", toolName, e);
            toolResult = new ToolResult("TOOL_UNAVAILABLE", Map.of(),
                    "工具执行异常", 0);
        }
        int duration = (int) (System.currentTimeMillis() - startMs);

        // 5. 审计
        String traceId = AiServiceUtils.generateUlid();
        toolAudit.audit(toolName, params, toolResult.resultCode(),
                context.sessionId(), traceId, duration, Instant.now());

        return new ToolExecutionResult(toolResult);
    }

    /**
     * 将工具结果格式化为消息内容。
     * 格式：[TOOL_RESULT:toolName]{json}
     */
    private String formatToolResult(String toolName, ToolResult result) {
        try {
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("resultCode", result.resultCode());
            if (result.data() != null) {
                resultData.put("data", result.data());
            }
            if (result.errorMessage() != null) {
                resultData.put("errorMessage", result.errorMessage());
            }
            String json = OBJECT_MAPPER.writeValueAsString(resultData);
            return "[TOOL_RESULT:" + toolName + "]" + json;
        } catch (Exception e) {
            log.warn("序列化工具结果失败: tool={}", toolName, e);
            return "[TOOL_RESULT:" + toolName + "]{\"resultCode\":\"" + result.resultCode() + "\"}";
        }
    }

    private String serializeArgs(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) return "";
        try {
            return OBJECT_MAPPER.writeValueAsString(arguments);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 工具选择校验：检测 LLM 选择的工具是否与用户意图明显不匹配。
     *
     * <p>基于用户消息中的关键词与工具用途进行简单匹配，
     * 当检测到明显偏差时返回纠正提示，否则返回 null。
     * 纠正提示会被注入为 SYSTEM 消息，让 LLM 在下一轮自行修正。</p>
     *
     * @param toolName LLM 选择的工具名
     * @param userMessage 用户原始消息
     * @return 不匹配时返回纠正提示，匹配时返回 null
     */
    private String detectToolMismatch(String toolName, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) return null;
        String lower = userMessage.toLowerCase();

        // 余额工具 vs 花呗/信用关键词
        if ("get_balance".equals(toolName)
                && containsAny(lower, "花呗", "信用额度", "额度")) {
            return "用户询问的是花呗/信用额度，应该使用 get_credit_summary 工具，而非 get_balance（余额查询）。";
        }

        // 信用额度工具 vs 余额关键词
        if ("get_credit_summary".equals(toolName)
                && containsAny(lower, "余额", "多少钱", "账户资金")
                && !containsAny(lower, "花呗", "信用", "额度")) {
            return "用户询问的是账户余额，应该使用 get_balance 工具，而非 get_credit_summary（信用额度查询）。";
        }

        // 交易明细 vs 花呗账单关键词
        if ("list_transactions".equals(toolName)
                && containsAny(lower, "花呗账单", "花呗的账单", "信用账单")) {
            return "用户询问的是花呗账单，应该使用 list_credit_bills 工具，而非 list_transactions（交易明细）。";
        }

        // 花呗账单 vs 交易明细关键词
        if ("list_credit_bills".equals(toolName)
                && containsAny(lower, "交易记录", "交易明细", "流水")
                && !containsAny(lower, "花呗", "信用")) {
            return "用户询问的是交易记录/流水，应该使用 list_transactions 工具，而非 list_credit_bills（花呗账单）。";
        }

        return null;
    }

    /**
     * 检查字符串是否包含任意关键词。
     *
     * @param text 待检查文本
     * @param keywords 关键词列表
     * @return 包含任一关键词时返回 true
     */
    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) return false;
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * AgentLoop 实现的工具执行处理器，委托给内部私有方法。
     * 将 AgentLoop 的工具执行能力（含安全检查、审计）暴露给自动推进策略。
     */
    private class AgentLoopToolHandler implements TransferFlowAutoAdvance.ToolExecutionHandler {
        @Override
        public ToolResult executeTool(AgentDecision.ToolCall toolCall,
                                      AgentContext context,
                                      Map<String, Object> accumulatedSlots) {
            return executeToolCall(toolCall, context, accumulatedSlots).toolResult();
        }

        @Override
        public String formatToolResult(String toolName, ToolResult result) {
            return AgentLoop.this.formatToolResult(toolName, result);
        }

        @Override
        public String serializeArgs(Map<String, Object> args) {
            return AgentLoop.this.serializeArgs(args);
        }
    }

    /**
     * 清除最终回复中可能泄露的内部工具结果标记。
     *
     * <p>委托给 {@link AiServiceUtils#sanitizeContent(String)}，
     * 确保 AgentLoop 和 AgentStreamService 使用相同的清理逻辑。</p>
     */
    private String sanitizeFinalContent(String content) {
        return AiServiceUtils.sanitizeContent(content);
    }

    /**
     * 从 LLM 文本回复中解析工具调用。
     *
     * <p>LLM 有时会以文本形式输出工具调用（如 [TOOL_CALL:toolName]{jsonArgs}），
     * 而非通过函数调用机制。本方法检测并解析这种模式，返回 ToolCall 决策。
     * 未匹配时返回 null。</p>
     *
     * <p>匹配模式：[TOOL_CALL:toolName]{json}</p>
     */
    private AgentDecision.ToolCall parseToolCallFromText(String content) {
        if (content == null || content.isBlank()) return null;

        // 匹配 [TOOL_CALL:toolName]{jsonArgs} 模式
        int start = content.indexOf("[TOOL_CALL:");
        if (start < 0) return null;

        int nameEnd = content.indexOf(']', start);
        if (nameEnd < 0) return null;

        String toolName = content.substring(start + "[TOOL_CALL:".length(), nameEnd);
        if (toolName.isBlank()) return null;

        // 查找紧随其后的 JSON 参数
        int jsonStart = content.indexOf('{', nameEnd);
        if (jsonStart < 0) return null;

        // 找到匹配的右括号
        int jsonEnd = findMatchingBrace(content, jsonStart);
        if (jsonEnd < 0) return null;

        String jsonArgs = content.substring(jsonStart, jsonEnd + 1);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> args = OBJECT_MAPPER.readValue(jsonArgs, Map.class);
            return new AgentDecision.ToolCall(toolName, args, 0);
        } catch (Exception e) {
            log.warn("解析文本工具调用参数失败: tool={}, json={}", toolName, jsonArgs, e);
            return null;
        }
    }

    /** 查找匹配的右花括号，支持嵌套对象 */
    private int findMatchingBrace(String text, int openPos) {
        int depth = 0;
        for (int i = openPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    /**
     * 从 LLM 文本中提取工具调用之前的内容。
     *
     * <p>当 LLM 以文本形式输出工具调用时，通常会在前面加一段过渡性回复
     * （如“好的，我来为您创建转账草稿...”）。本方法提取这段文本，
     * 以便保留到消息历史中，避免 LLM 在下一轮重复生成相同内容。</p>
     */
    private String extractTextBeforeToolCall(String content) {
        if (content == null) return null;
        int start = content.indexOf("[TOOL_CALL:");
        if (start <= 0) return null;
        return content.substring(0, start).trim();
    }

    /**
     * 判断工具是否为只读查询类（同一工具只需执行一次）。
     *
     * <p>只读查询类工具的去重策略：按工具名去重，不论参数差异。
     * 避免 LLM 用不同参数重复调用同一查询工具（如两次 list_transactions 不同 limit），
     * 导致前端渲染多个工具结果卡片。</p>
     */
    private boolean isReadOnlyQueryTool(String toolName) {
        return "get_balance".equals(toolName)
                || "get_credit_summary".equals(toolName)
                || "list_transactions".equals(toolName)
                || "list_credit_bills".equals(toolName)
                || "get_transaction_status".equals(toolName)
                || "get_account_summary".equals(toolName)
                || "search_payees".equals(toolName);
    }

    /**
     * 工具执行结果包装（内部使用）。
     */
    private record ToolExecutionResult(ToolResult toolResult) {}
}
