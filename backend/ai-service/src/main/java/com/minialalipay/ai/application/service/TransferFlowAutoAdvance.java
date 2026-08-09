package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.application.port.AgentDecision;
import com.minialalipay.ai.application.port.ToolResult;
import com.minialalipay.ai.application.service.AgentLoop.AgentContext;
import com.minialalipay.ai.application.service.AgentLoop.ToolResultRecord;
import com.minialalipay.ai.application.service.ResultInterpreter;
import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.domain.agent.MessageRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 转账流程自动推进策略。
 *
 * <p>当 LLM 重复调用中间步骤（{@code create_transfer_draft}/{@code validate_transfer_draft}）
 * 或提前返回 FinalReply 但转账流程未完成时，自动执行下一步工具，避免流程卡顿。</p>
 *
 * <h3>转账流程状态机</h3>
 * <pre>
 * search_payees → create_transfer_draft → validate_transfer_draft → prepare_confirmation_card
 * </pre>
 *
 * <h3>与 AgentLoop 的协作</h3>
 * <ul>
 *   <li>本类负责：流程状态检测、下一步工具决策</li>
 *   <li>AgentLoop 负责：工具执行、安全检查、审计、回调通知</li>
 *   <li>通过函数式接口解耦，本类不依赖 AgentLoop 内部实现</li>
 * </ul>
 */
public class TransferFlowAutoAdvance {

    private static final Logger log = LoggerFactory.getLogger(TransferFlowAutoAdvance.class);

    /** 最多自动推进 3 步（search→draft→validate→confirm） */
    private static final int MAX_AUTO_STEPS = 3;

    private final ResultInterpreter resultInterpreter;

    /**
     * 工具执行回调接口，由 AgentLoop 实现。
     *
     * <p>将 AgentLoop 的工具执行能力（含安全检查、审计）暴露给自动推进策略，
     * 避免重复实现安全逻辑。</p>
     */
    public interface ToolExecutionHandler {
        /**
         * 执行工具调用并返回结果。
         *
         * @param toolCall 工具调用（工具名 + 参数）
         * @param context Agent 执行上下文
         * @param accumulatedSlots 当前累积槽位
         * @return 工具执行结果
         */
        ToolResult executeTool(AgentDecision.ToolCall toolCall,
                               AgentContext context,
                               Map<String, Object> accumulatedSlots);

        /**
         * 格式化工具结果为消息内容。
         *
         * @param toolName 工具名
         * @param result 工具结果
         * @return 格式化后的消息内容
         */
        String formatToolResult(String toolName, ToolResult result);

        /**
         * 序列化工具参数为 JSON 字符串。
         *
         * @param args 工具参数
         * @return JSON 字符串
         */
        String serializeArgs(Map<String, Object> args);
    }

    public TransferFlowAutoAdvance(ResultInterpreter resultInterpreter) {
        this.resultInterpreter = resultInterpreter;
    }

    /**
     * 检测未完成的转账流程。
     *
     * <p>当 LLM 提前生成文本回复（FinalReply）但工具执行链不完整时，
     * 返回推进提示让 LLM 继续调用下一步工具。返回 null 表示流程已完成或无需推进。</p>
     *
     * @param executedTools 已执行的工具名列表
     * @return 推进提示文本，或 null 表示流程已完成
     */
    public String detectIncompleteFlow(List<String> executedTools) {
        if (executedTools == null || executedTools.isEmpty()) return null;

        boolean hasSearch = executedTools.contains("search_payees");
        boolean hasDraft = executedTools.contains("create_transfer_draft");
        boolean hasValidate = executedTools.contains("validate_transfer_draft");
        boolean hasConfirm = executedTools.contains("prepare_confirmation_card");

        if (hasSearch && !hasDraft) {
            return "已找到收款人，下一步必须调用 create_transfer_draft 创建转账草稿。" +
                    "请从工具结果中提取 payeeId，结合用户消息中的金额（元转分）作为参数。";
        }
        if (hasDraft && !hasValidate) {
            return "转账草稿已创建，下一步必须调用 validate_transfer_draft 进行校验。" +
                    "请从工具结果中提取 draftId 作为参数。";
        }
        if (hasValidate && !hasConfirm) {
            return "转账草稿已校验通过，下一步必须调用 prepare_confirmation_card 生成确认卡片。" +
                    "请从工具结果中提取 draftId 作为参数。";
        }

        return null;
    }

    /**
     * FinalReply 自动推进：当 LLM 返回文本回复但转账流程未完成时，
     * 直接执行下一步工具。
     *
     * <p>支持连续自动推进多步，避免 LLM 在每步后都返回 FinalReply 导致流程卡顿。</p>
     *
     * @param executedTools 已执行工具列表（可变，推进后追加）
     * @param accumulatedSlots 累积槽位（可变，推进后更新）
     * @param messages 消息列表（可变，推进后追加工具结果）
     * @param executedToolKeys 去重集合（可变，推进后追加）
     * @param previousToolResults 历史工具结果（可变，推进后追加）
     * @param context Agent 执行上下文
     * @param iteration 当前迭代轮次
     * @param toolResults 工具结果摘要列表（可变，推进后追加）
     * @param handler 工具执行处理器
     * @return true 表示成功推进了至少一步
     */
    public boolean tryAutoAdvanceFromFinalReply(
            List<String> executedTools,
            Map<String, Object> accumulatedSlots,
            List<ChatMessage> messages,
            Set<String> executedToolKeys,
            Map<String, ToolResult> previousToolResults,
            AgentContext context,
            int iteration,
            List<ToolResultRecord> toolResults,
            ToolExecutionHandler handler
    ) {
        if (executedTools == null || executedTools.isEmpty()) return false;

        int advancedCount = 0;
        for (int step = 0; step < MAX_AUTO_STEPS; step++) {
            NextStepInfo nextStep = determineNextStep(executedTools, accumulatedSlots,
                    context.userMessage());
            if (nextStep == null) {
                return advancedCount > 0;
            }

            log.info("FinalReply 自动推进 (step {}): 已执行={}, 下一步工具={}, args={}",
                    step, executedTools, nextStep.toolName, nextStep.args);

            ToolResult lastResult = executeAndRecord(
                    nextStep.toolName, nextStep.args,
                    executedTools, accumulatedSlots, messages,
                    executedToolKeys, previousToolResults,
                    context, iteration, toolResults, handler);
            advancedCount++;

            // 如果工具执行失败，停止自动推进
            if (!lastResult.isSuccess()) {
                log.warn("自动推进中断: 工具执行失败, tool={}, resultCode={}",
                        nextStep.toolName, lastResult.resultCode());
                return true;
            }
        }

        log.warn("FinalReply 自动推进达到最大步数 {}", MAX_AUTO_STEPS);
        return true;
    }

    /**
     * 转账流程自动推进：当 LLM 重复调用中间步骤工具时，
     * 自动执行下一步（prepare_confirmation_card），避免死循环。
     *
     * @param repeatedToolName 重复调用的工具名
     * @param executedTools 已执行工具列表
     * @param accumulatedSlots 累积槽位
     * @param messages 消息列表
     * @param executedToolKeys 去重集合
     * @param previousToolResults 历史工具结果
     * @param context Agent 执行上下文
     * @param iteration 当前迭代轮次
     * @param toolResults 工具结果摘要列表
     * @param handler 工具执行处理器
     * @return true 表示自动推进成功
     */
    public boolean tryAutoAdvanceTransferFlow(
            String repeatedToolName,
            List<String> executedTools,
            Map<String, Object> accumulatedSlots,
            List<ChatMessage> messages,
            Set<String> executedToolKeys,
            Map<String, ToolResult> previousToolResults,
            AgentContext context,
            int iteration,
            List<ToolResultRecord> toolResults,
            ToolExecutionHandler handler
    ) {
        if (!"create_transfer_draft".equals(repeatedToolName)
                && !"validate_transfer_draft".equals(repeatedToolName)) {
            return false;
        }

        boolean hasValidate = executedTools.contains("validate_transfer_draft");
        boolean hasConfirm = executedTools.contains("prepare_confirmation_card");
        if (!hasValidate || hasConfirm) {
            return false;
        }

        Object draftIdObj = accumulatedSlots.get("draftId");
        if (draftIdObj == null || draftIdObj.toString().isBlank()) {
            log.warn("自动推进失败: accumulatedSlots 中无 draftId");
            return false;
        }
        String draftId = draftIdObj.toString();

        log.info("转账流程自动推进: 重复具={}, 自动执行 prepare_confirmation_card, draftId={}",
                repeatedToolName, draftId);

        Map<String, Object> confirmArgs = Map.of("draftId", draftId);
        ToolResult result = executeAndRecord(
                "prepare_confirmation_card", confirmArgs,
                executedTools, accumulatedSlots, messages,
                executedToolKeys, previousToolResults,
                context, iteration, toolResults, handler);
        return result.isSuccess() || !executedTools.isEmpty();
    }

    // ---- 内部方法 ----

    /**
     * 根据当前流程状态确定下一步应执行的工具。
     *
     * <p>当 {@code accumulatedSlots} 中缺少 {@code amountFen} 时，
     * 尝试从用户原始消息中提取金额作为兜底（支持阿拉伯数字和中文数字）。</p>
     *
     * @param executedTools 已执行的工具列表
     * @param slots 累积槽位
     * @param userMessage 用户原始消息（用于金额兜底提取）
     * @return 下一步工具信息，或 null 表示流程已完成/无法继续
     */
    private NextStepInfo determineNextStep(List<String> executedTools,
                                            Map<String, Object> slots,
                                            String userMessage) {
        boolean hasSearch = executedTools.contains("search_payees");
        boolean hasDraft = executedTools.contains("create_transfer_draft");
        boolean hasValidate = executedTools.contains("validate_transfer_draft");
        boolean hasConfirm = executedTools.contains("prepare_confirmation_card");

        if (hasSearch && !hasDraft) {
            Object payeeId = slots.get("payeeId");
            Object amountFen = slots.get("amountFen");
            // 兜底：从用户消息中提取金额
            if (amountFen == null && userMessage != null) {
                Long parsed = AiServiceUtils.parseAmountFromText(userMessage);
                if (parsed != null) {
                    amountFen = parsed;
                    log.info("从用户消息中提取到金额: userMessage={}, amountFen={}", userMessage, parsed);
                }
            }
            if (payeeId == null || amountFen == null) {
                log.warn("FinalReply 自动推进失败: 缺少 payeeId 或 amountFen, slots={}, hasUserMessage={}",
                        slots.keySet(), userMessage != null);
                return null;
            }
            return new NextStepInfo("create_transfer_draft",
                    Map.of("payeeId", payeeId.toString(), "amountFen", amountFen));
        }
        if (hasDraft && !hasValidate) {
            Object draftId = slots.get("draftId");
            if (draftId == null || draftId.toString().isBlank()) {
                log.warn("FinalReply 自动推进失败: 缺少 draftId");
                return null;
            }
            return new NextStepInfo("validate_transfer_draft",
                    Map.of("draftId", draftId.toString()));
        }
        if (hasValidate && !hasConfirm) {
            Object draftId = slots.get("draftId");
            if (draftId == null || draftId.toString().isBlank()) {
                log.warn("FinalReply 自动推进失败: 缺少 draftId");
                return null;
            }
            return new NextStepInfo("prepare_confirmation_card",
                    Map.of("draftId", draftId.toString()));
        }

        return null;
    }

    /**
     * 执行单个工具并记录结果到所有相关集合。
     *
     * @return 工具执行结果，调用方可通过 {@code isSuccess()} 判断是否继续推进
     */
    private ToolResult executeAndRecord(
            String toolName, Map<String, Object> args,
            List<String> executedTools,
            Map<String, Object> accumulatedSlots,
            List<ChatMessage> messages,
            Set<String> executedToolKeys,
            Map<String, ToolResult> previousToolResults,
            AgentContext context,
            int iteration,
            List<ToolResultRecord> toolResults,
            ToolExecutionHandler handler
    ) {
        AgentDecision.ToolCall toolCall = new AgentDecision.ToolCall(toolName, args, 0);
        ToolResult result = handler.executeTool(toolCall, context, accumulatedSlots);

        String key = toolName + ":" + handler.serializeArgs(args);
        executedToolKeys.add(key);
        previousToolResults.put(key, result);
        executedTools.add(toolName);

        String status = result.isSuccess() ? "success" : "failed";
        String summary = result.isSuccess()
                ? resultInterpreter.interpret(toolName, result)
                : (result.errorMessage() != null ? result.errorMessage() : "工具执行失败");
        Map<String, Object> resultData = result.data() != null
                ? new HashMap<>(result.data()) : Map.of();

        if (context.callback() != null) {
            context.callback().onToolCall(toolName, "running");
            context.callback().onToolResult(toolName, status, summary, resultData);
        }
        toolResults.add(new ToolResultRecord(toolName, status, summary, resultData));

        if (result.data() != null) {
            accumulatedSlots.putAll(result.data());
        }

        String toolResultJson = handler.formatToolResult(toolName, result);
        messages.add(new ChatMessage(MessageRole.SYSTEM, toolResultJson));

        log.info("自动推进步骤完成: tool={}, resultCode={}, iteration={}",
                toolName, result.resultCode(), iteration);

        return result;
    }

    /** 下一步工具信息（内部使用） */
    private record NextStepInfo(String toolName, Map<String, Object> args) {}
}
