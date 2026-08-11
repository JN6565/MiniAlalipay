package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.AiServiceUtils;
import com.minialalipay.ai.application.port.AgentDecision;
import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.service.StructuredOutputValidator;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.domain.agent.IntentType;
import com.minialalipay.ai.domain.agent.MessageRole;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * OpenAI 兼容协议的语言模型适配器（基于 Spring AI）。
 *
 * <p>通过 Spring AI {@link ChatModel} 调用 DeepSeek 或其他 OpenAI 兼容的 LLM。
 * 当未配置 API Key 时使用关键词匹配 Mock，不依赖真实 LLM。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>API Key 只从环境变量注入，禁止硬编码</li>
 *   <li>返回的槽位不包含支付密码、令牌或确认上下文</li>
 *   <li>{@code payeeId} 须来自受控工具返回值</li>
 * </ul>
 */
@Component
public class OpenAiLanguageModelAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLanguageModelAdapter.class);

    /** 结构化输出格式指令，通过 ai.prompt.output-format 配置 */
    private final String outputFormatInstruction;

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final String model;
    private final Semaphore semaphore;
    private final CircuitBreaker circuitBreaker;
    private final boolean mockMode;
    private final StructuredOutputValidator validator;

    public OpenAiLanguageModelAdapter(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model,
            @Value("${ai.llm.max-concurrent:20}") int maxConcurrent,
            @Value("${ai.llm.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${ai.llm.circuit-breaker.open-state-duration:30s}") String openStateDuration,
            @Value("${ai.prompt.output-format:}" ) String outputFormatInstruction,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<StreamingChatModel> streamingChatModelProvider,
            StructuredOutputValidator validator
    ) {
        this.model = model;
        this.validator = validator;
        this.outputFormatInstruction = (outputFormatInstruction != null && !outputFormatInstruction.isBlank())
                ? outputFormatInstruction : getDefaultOutputFormatInstruction();
        // API Key 以 "sk-" 开头 → 真实模式；否则 → Mock 模式
        this.mockMode = apiKey == null || apiKey.isBlank() || !apiKey.startsWith("sk-");
        // mock 模式下不获取 ChatModel（Spring AI 必须删除了 ChatAutoConfiguration 排除才能创建该 Bean）
        this.chatModel = mockMode ? null : chatModelProvider.getIfAvailable();
        this.streamingChatModel = mockMode ? null : streamingChatModelProvider.getIfAvailable();
        this.semaphore = new Semaphore(maxConcurrent);
        this.circuitBreaker = new CircuitBreaker(failureThreshold, parseDurationSeconds(openStateDuration));
        log.info("LLM 适配器启动: mode={}, model={}, apiKeyPrefix={}",
                mockMode ? "Mock" : "Spring AI + DeepSeek", model,
                apiKey.isEmpty() ? "(空)" : apiKey.substring(0, Math.min(5, apiKey.length())) + "***");
    }

    /**
     * 调用语言模型，返回结构化的意图、槽位和自然语言回复。
     *
     * @param systemPrompt 系统提示词
     * @param history      近期对话历史（按时间正序）
     * @param userMessage  当前用户输入（已脱敏）
     * @return 含意图、槽位和回复文本的结构化响应
     */
    public ChatResponse chat(String systemPrompt, List<ChatMessage> history, String userMessage) {
        circuitBreaker.assertNotOpen();
        if (!semaphore.tryAcquire()) {
            throw new BusinessException(AgentErrorCode.AGENT_BUSY);
        }
        try {
            ChatResponse response = mockMode
                    ? mockLlmResponse(userMessage)
                    : realLlmCall(systemPrompt, history, userMessage);
            circuitBreaker.recordSuccess();
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LLM 调用异常: {}", e.getMessage());
            circuitBreaker.recordFailure();
            if (!mockMode) {
                log.info("真实 LLM 不可用，降级到 Mock 响应");
                return mockLlmResponse(userMessage);
            }
            throw new BusinessException(AgentErrorCode.LLM_UNAVAILABLE);
        } finally {
            semaphore.release();
        }
    }

    /**
     * 流式调用语言模型，在生成过程中逐步回调文本增量。
     *
     * <p>真实模式下使用 Spring AI {@link StreamingChatModel} 的流式 API，
     * 每收到一个 Token 即回调 {@code onContentDelta}，实现“边生成边推送”效果。
     * Mock 模式或流式不可用时回退到阻塞式调用。</p>
     */
    public ChatResponse streamChat(String systemPrompt, List<ChatMessage> history,
                                   String userMessage, Consumer<String> onContentDelta) {
        circuitBreaker.assertNotOpen();
        if (!semaphore.tryAcquire()) {
            throw new BusinessException(AgentErrorCode.AGENT_BUSY);
        }
        try {
            if (mockMode || streamingChatModel == null) {
                // Mock 模式或流式模型不可用：回退到阻塞式调用
                ChatResponse response = mockMode
                        ? mockLlmResponse(userMessage)
                        : realLlmCall(systemPrompt, history, userMessage);
                if (response.content() != null) {
                    onContentDelta.accept(response.content());
                }
                circuitBreaker.recordSuccess();
                return response;
            }
            // 真实流式模式
            ChatResponse result = realLlmStreamCall(systemPrompt, history, userMessage, onContentDelta);
            circuitBreaker.recordSuccess();
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LLM 流式调用异常: {}", e.getMessage());
            circuitBreaker.recordFailure();
            log.info("真实 LLM 不可用，降级到 Mock 响应");
            return mockLlmResponse(userMessage);
        } finally {
            semaphore.release();
        }
    }

    /**
     * 纯自然语言流式调用，不附加结构化输出格式指令。
     *
     * <p>用于工具执行后的第二次 LLM 调用：此时意图已由第一次调用确定，
     * 只需让 LLM 基于工具结果生成自然语言回复，不再要求 JSON 格式输出。
     * 避免结构化 JSON 被直接流式推送到前端。</p>
     */
    public ChatResponse streamNaturalLanguageChat(String systemPrompt, List<ChatMessage> history,
                                                  String userMessage, Consumer<String> onContentDelta) {
        circuitBreaker.assertNotOpen();
        if (!semaphore.tryAcquire()) {
            throw new BusinessException(AgentErrorCode.AGENT_BUSY);
        }
        try {
            if (mockMode || streamingChatModel == null) {
                ChatResponse response = mockMode
                        ? mockLlmResponse(userMessage)
                        : realLlmCall(systemPrompt, history, userMessage);
                if (response.content() != null) {
                    onContentDelta.accept(response.content());
                }
                circuitBreaker.recordSuccess();
                return response;
            }
            // 真实流式模式：不附加 outputFormatInstruction
            ChatResponse result = realLlmStreamCallPlain(systemPrompt, history, userMessage, onContentDelta);
            circuitBreaker.recordSuccess();
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("LLM 自然语言流式调用异常: {}", e.getMessage());
            circuitBreaker.recordFailure();
            log.info("真实 LLM 不可用，降级到 Mock 响应");
            return mockLlmResponse(userMessage);
        } finally {
            semaphore.release();
        }
    }

    // ---- Agent 单步推理（function calling） ----

    /**
     * Agent 单步推理：通过 Spring AI function calling 让 LLM 自主选择工具或给出最终回复。
     *
     * <p>真实模式：将工具定义注册为 Spring AI FunctionCallback，检查响应中的 tool_calls。
     * Mock 模式：关键词匹配用户消息，返回模拟的工具调用决策。</p>
     */
    public AgentDecision agentStep(List<ChatMessage> messages,
                                   List<ToolCatalog.ToolDefinition> tools) {
        circuitBreaker.assertNotOpen();
        if (!semaphore.tryAcquire()) {
            throw new BusinessException(AgentErrorCode.AGENT_BUSY);
        }
        try {
            AgentDecision decision = mockMode
                    ? mockAgentStep(messages)
                    : realAgentStep(messages, tools);
            circuitBreaker.recordSuccess();
            return decision;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Agent step 异常: {}", e.getMessage());
            circuitBreaker.recordFailure();
            if (!mockMode) {
                log.info("真实 LLM 不可用，降级到 Mock agentStep");
                return mockAgentStep(messages);
            }
            throw new BusinessException(AgentErrorCode.LLM_UNAVAILABLE);
        } finally {
            semaphore.release();
        }
    }

    /**
     * 真实 LLM agentStep：使用 Spring AI function calling。
     *
     * <p>采用两阶段调用：
     * <ol>
     *   <li>第一次调用：注册工具定义，让 LLM 选择工具（internalToolExecutionEnabled=false 阻止自动执行）</li>
     *   <li>若 LLM 返回 tool_calls：由 AgentLoop 执行工具后，将真实结果以 ToolResponseMessage 形式回传</li>
     *   <li>第二次调用：携带工具结果，让 LLM 基于真实数据决定下一步</li>
     * </ol>
     * </p>
     */
    private AgentDecision realAgentStep(List<ChatMessage> messages,
                                        List<ToolCatalog.ToolDefinition> tools) {
        // 1. 将工具定义转换为 Spring AI ToolCallback（回调返回占位符，实际执行由 AgentLoop 完成）
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        for (ToolCatalog.ToolDefinition tool : tools) {
            String jsonSchema = mapToJsonSchema(tool.inputSchema());
            log.info("注册工具: name={}, description={}, inputSchema={}",
                    tool.toolName(), tool.description(), jsonSchema);
            toolCallbacks.add(FunctionToolCallback.builder(
                            tool.toolName(), (Map<String, Object> inputMap) -> "OK")
                    .description(tool.description())
                    .inputSchema(jsonSchema)
                    .inputType(Map.class)
                    .build());
        }

        // 2. 转换 ChatMessage 为 Spring AI Message
        List<Message> springMessages = convertToSpringMessages(messages);

        // 3. 构建 Prompt，附带 function callbacks
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.1)
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();
        Prompt prompt = new Prompt(springMessages, options);

        // 4. 调用 LLM
        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return new AgentDecision.FinalReply("抱歉，AI 服务暂时繁忙，请稍后重试。", 10);
        }

        AssistantMessage assistantMsg = response.getResult().getOutput();
        String content = assistantMsg.getText();
        int estimatedTokens = estimateTokensFromMessages(messages);

        // 5. 检查 tool_calls
        if (assistantMsg.hasToolCalls()) {
            List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
            List<AgentDecision.ToolCall> decisions = toolCalls.stream()
                    .map(call -> new AgentDecision.ToolCall(
                            call.name(), parseToolCallArguments(call.arguments()), estimatedTokens, call.id()))
                    .toList();
            log.info("Agent step 决策: toolCallCount={}, tools={}", decisions.size(),
                    decisions.stream().map(AgentDecision.ToolCall::toolName).toList());
            return decisions.size() == 1
                    ? decisions.get(0)
                    : new AgentDecision.ToolCalls(decisions, estimatedTokens);
        }

        // 6. 无 tool_calls → FinalReply
        if (content == null || content.isBlank()) {
            return new AgentDecision.FinalReply("抱歉，AI 服务暂时繁忙，请稍后重试。", estimatedTokens);
        }
        log.info("Agent step 决策: finalReply(无工具调用), content={}", content.length() > 200 ? content.substring(0, 200) + "..." : content);
        return new AgentDecision.FinalReply(content, estimatedTokens);
    }

    /**
     * 基于真实工具结果进行第二次 LLM 调用。
     *
     * <p>将工具结果以 ToolResponseMessage 形式传入，让 LLM 基于真实数据做决策。
     * 若 LLM 再次选择工具调用，则返回 ToolCall 由 AgentLoop 继续执行；
     * 若 LLM 生成最终回复，则返回 FinalReply。</p>
     *
     * @param messages 当前消息列表（含工具结果）
     * @param tools 可用工具定义
     * @param toolCallId 上一次工具调用的 ID
     * @param toolName 上一次工具调用的名称
     * @param actualResult 工具执行的真实结果 JSON
     * @return Agent 决策（ToolCall 或 FinalReply）
     */
    public AgentDecision agentStepWithToolResult(
            List<ChatMessage> messages,
            List<ToolCatalog.ToolDefinition> tools,
            String toolCallId, String toolName, String actualResult) {

        // 构建工具回调（第二次调用仍需注册工具定义，以便 LLM 可继续调用其他工具）
        List<ToolCallback> toolCallbacks = new ArrayList<>();
        for (ToolCatalog.ToolDefinition tool : tools) {
            String jsonSchema = mapToJsonSchema(tool.inputSchema());
            toolCallbacks.add(FunctionToolCallback.builder(
                            tool.toolName(), (Map<String, Object> inputMap) -> "OK")
                    .description(tool.description())
                    .inputSchema(jsonSchema)
                    .inputType(Map.class)
                    .build());
        }

        // 转换消息并追加真实的工具结果（ToolResponseMessage）
        List<Message> springMessages = convertToSpringMessages(messages);
        ToolResponseMessage.ToolResponse toolResponse =
                new ToolResponseMessage.ToolResponse(toolCallId, toolName, actualResult);
        springMessages.add(new ToolResponseMessage(List.of(toolResponse)));

        // 构建 Prompt
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.1)
                .toolCallbacks(toolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();
        Prompt prompt = new Prompt(springMessages, options);

        org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);
        if (response.getResult() == null || response.getResult().getOutput() == null) {
            return new AgentDecision.FinalReply("抱歉，AI 服务暂时繁忙，请稍后重试。", 10);
        }

        AssistantMessage assistantMsg = response.getResult().getOutput();
        int estimatedTokens = estimateTokensFromMessages(messages);

        if (assistantMsg.hasToolCalls()) {
            List<AssistantMessage.ToolCall> toolCalls = assistantMsg.getToolCalls();
            List<AgentDecision.ToolCall> decisions = toolCalls.stream()
                    .map(call -> new AgentDecision.ToolCall(
                            call.name(), parseToolCallArguments(call.arguments()), estimatedTokens, call.id()))
                    .toList();
            log.info("Agent step(工具结果后) 决策: toolCallCount={}, tools={}", decisions.size(),
                    decisions.stream().map(AgentDecision.ToolCall::toolName).toList());
            return decisions.size() == 1
                    ? decisions.get(0)
                    : new AgentDecision.ToolCalls(decisions, estimatedTokens);
        }

        String content = assistantMsg.getText();
        if (content == null || content.isBlank()) {
            return new AgentDecision.FinalReply("抱歉，AI 服务暂时繁忙，请稍后重试。", estimatedTokens);
        }
        log.info("Agent step(工具结果后) 决策: finalReply, content={}",
                content.length() > 200 ? content.substring(0, 200) + "..." : content);
        return new AgentDecision.FinalReply(content, estimatedTokens);
    }

    /**
     * Mock agentStep：关键词匹配返回模拟工具调用。
     */
    private AgentDecision mockAgentStep(List<ChatMessage> messages) {
        String lastUserMessage = getLastUserMessage(messages);
        String lower = lastUserMessage.toLowerCase();

        // 检查消息列表中是否已有工具结果（用于多步推理）
        boolean hasToolResults = messages.stream()
                .anyMatch(m -> m.role() == MessageRole.SYSTEM
                        && m.content() != null && m.content().startsWith("[TOOL_RESULT:"));

        if (hasToolResults) {
            // 已有工具结果，检查是否需要下一步工具或给出最终回复
            String lastToolResult = messages.stream()
                    .filter(m -> m.role() == MessageRole.SYSTEM
                            && m.content() != null && m.content().startsWith("[TOOL_RESULT:"))
                    .reduce((first, second) -> second)
                    .map(ChatMessage::content)
                    .orElse("");
            return mockAgentStepAfterTool(lastUserMessage, lastToolResult, lower);
        }

        // 首次推理：根据用户消息决定调用哪个工具
        if (containsAny(lower, "转账", "转给", "汇款", "转钱")) {
            String payeeName = extractPayeeName(lastUserMessage);
            Map<String, Object> args = new HashMap<>();
            if (payeeName != null && !payeeName.isBlank()) {
                args.put("query", payeeName);
            } else {
                args.put("query", "默认");
            }
            return new AgentDecision.ToolCall("search_payees", args, 30);
        }
        if (containsAny(lower, "余额", "多少钱", "查余额")) {
            return new AgentDecision.ToolCall("get_balance", Map.of(), 25);
        }
        if (containsAny(lower, "账单", "花呗账单", "本月账单")) {
            return new AgentDecision.ToolCall("list_credit_bills", Map.of("limit", 10), 30);
        }
        if (containsAny(lower, "交易记录", "交易明细", "流水")) {
            Map<String, Object> args = new HashMap<>();
            args.put("limit", 10);
            if (containsAny(lower, "支出", "花", "转出", "付出")) {
                args.put("direction", "OUT");
            } else if (containsAny(lower, "收入", "收款", "转入", "入账")) {
                args.put("direction", "IN");
            }
            return new AgentDecision.ToolCall("list_transactions", args, 35);
        }
        if (containsAny(lower, "交易状态", "转到哪了")) {
            String txId = extractTransactionId(lastUserMessage);
            if (txId != null && !txId.isBlank()) {
                return new AgentDecision.ToolCall("get_transaction_status",
                        Map.of("transactionId", txId), 25);
            }
            return new AgentDecision.FinalReply(
                    "请提供您要查询的交易编号（可在交易记录中查看）。", 20);
        }
        if (containsAny(lower, "花呗", "信用", "额度")) {
            if (containsAny(lower, "还", "还款")) {
                if (containsAny(lower, "全部还清", "全额还", "还清全部", "一次还清")) {
                    return new AgentDecision.FinalReply(
                            "抱歉，暂不支持全部还清功能。请告诉我您想还款的具体金额（如'还200元'）。", 30);
                }
                long amountFen = inferAmountFen(lastUserMessage);
                if (amountFen > 0) {
                    return new AgentDecision.ToolCall("create_credit_repayment_draft",
                            Map.of("amountFen", amountFen), 28);
                }
                return new AgentDecision.FinalReply(
                        "您的花呗待还总额为 0 元。请问要还多少？", 28);
            }
            return new AgentDecision.ToolCall("get_credit_summary", Map.of(), 28);
        }
        if (containsAny(lower, "找", "搜索", "收款人")) {
            return new AgentDecision.ToolCall("search_payees",
                    Map.of("query", "默认", "limit", 10), 18);
        }
        return new AgentDecision.FinalReply(
                "抱歉，我没有理解您的意图。我可以帮您：转账、查余额、查交易明细、查花呗账单、还花呗、搜收款人。", 40);
    }

    /**
     * Mock 模式：工具调用后的下一步决策。
     */
    private AgentDecision mockAgentStepAfterTool(String originalUserMessage,
                                                  String lastToolResult, String lower) {
        // 根据原始意图和工具结果决定下一步
        if (containsAny(lower, "转账", "转给", "汇款", "转钱")) {
            // 转账流程：search_payees → create_transfer_draft → validate → prepare_confirmation
            if (lastToolResult.contains("[TOOL_RESULT:search_payees]")) {
                long amountFen = inferAmountFen(originalUserMessage);
                if (amountFen <= 0) amountFen = 10000L; // Mock 默认 100 元
                Map<String, Object> args = new HashMap<>();
                args.put("payeeId", "mock-payee-001");
                args.put("amountFen", amountFen);
                return new AgentDecision.ToolCall("create_transfer_draft", args, 35);
            }
            if (lastToolResult.contains("[TOOL_RESULT:create_transfer_draft]")) {
                return new AgentDecision.ToolCall("validate_transfer_draft",
                        Map.of("draftId", "mock-draft-001"), 30);
            }
            if (lastToolResult.contains("[TOOL_RESULT:validate_transfer_draft]")) {
                return new AgentDecision.ToolCall("prepare_confirmation_card",
                        Map.of("draftId", "mock-draft-001"), 30);
            }
            if (lastToolResult.contains("[TOOL_RESULT:prepare_confirmation_card]")) {
                return new AgentDecision.FinalReply(
                        "转账信息已准备好，请在确认卡片中核对信息后完成支付。", 40);
            }
        }
        if (containsAny(lower, "还款") && lastToolResult.contains("[TOOL_RESULT:create_credit_repayment_draft]")) {
            return new AgentDecision.FinalReply(
                    "还款草稿已保存，请确认还款金额。", 30);
        }
        // 查询类工具结果 → 生成最终回复
        return new AgentDecision.FinalReply(
                formatMockToolResult(lastToolResult), 35);
    }

    /**
     * 将 Mock 工具结果格式化为自然语言回复。
     */
    private String formatMockToolResult(String toolResult) {
        if (toolResult.contains("get_balance")) {
            return "您当前账户可用余额为 10,000.00 元。";
        }
        if (toolResult.contains("list_transactions")) {
            return "以下是您最近的交易明细……需要查看更多吗？";
        }
        if (toolResult.contains("get_credit_summary")) {
            return "您的 Mini 花呗总额度 5,000.00 元，已用 0 元。";
        }
        if (toolResult.contains("list_credit_bills")) {
            return "您本月的花呗账单如下：应还 0 元，未出账 0 元。";
        }
        if (toolResult.contains("search_payees")) {
            return "为您找到 1 位候选收款人。";
        }
        if (toolResult.contains("get_transaction_status")) {
            return "该笔交易当前状态: SUCCESS。";
        }
        return "操作已完成。";
    }

    // ---- agentStep 辅助方法 ----

    /**
     * 将 ChatMessage 列表转换为 Spring AI Message 列表。
     * 识别 [TOOL_RESULT:toolName] 前缀的 SYSTEM 消息并转换为 ToolResponseMessage。
     */
    private List<Message> convertToSpringMessages(List<ChatMessage> messages) {
        List<Message> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            // 所有消息统一转换为 Spring AI Message
            // 工具结果以 SYSTEM 消息形式传递，前缀 [TOOL_RESULT:toolName]
            result.add(switch (msg.role()) {
                case USER -> new UserMessage(msg.content());
                case ASSISTANT -> new AssistantMessage(msg.content());
                case SYSTEM -> new SystemMessage(msg.content());
            });
        }
        return result;
    }

    /**
     * 解析 tool call 的 JSON 参数字符串为 Map。
     */
    private Map<String, Object> parseToolCallArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(argumentsJson, Map.class);
            return parsed;
        } catch (Exception e) {
            log.warn("解析 tool call 参数失败: {}", argumentsJson, e);
            return Map.of();
        }
    }

    /**
     * 将 ToolDefinition 的 inputSchema (Map) 转换为 JSON Schema 字符串。
     */
    private String mapToJsonSchema(Map<String, Object> inputSchema) {
        if (inputSchema == null || inputSchema.isEmpty()) {
            return "";
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(inputSchema);
        } catch (Exception e) {
            log.warn("序列化 inputSchema 失败: {}", inputSchema, e);
            return "";
        }
    }

    /**
     * 从消息列表中提取最后一条用户消息内容。
     */
    private String getLastUserMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.role() == MessageRole.USER) {
                return msg.content();
            }
        }
        return "";
    }

    /**
     * 估算消息列表的 Token 数。
     */
    private int estimateTokensFromMessages(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage msg : messages) {
            total += AiServiceUtils.estimateTokens(msg.content());
        }
        return total;
    }

    // ---- 真实 LLM 调用 ----

    /**
     * 通过 Spring AI ChatModel 调用 DeepSeek 等 OpenAI 兼容 API。
     */
    private ChatResponse realLlmCall(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt + outputFormatInstruction));
        for (ChatMessage msg : history) {
            messages.add(switch (msg.role()) {
                case USER -> new UserMessage(msg.content());
                case ASSISTANT -> new AssistantMessage(msg.content());
                case SYSTEM -> new SystemMessage(msg.content());
            });
        }
        messages.add(new UserMessage(userMessage));

        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.1)
                .build());

        String content = chatModel.call(prompt).getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            return fallbackResponse();
        }

        // 结构化校验
        try {
            StructuredOutputValidator.ValidatedResponse validated = validator.validate(content);
            log.debug("结构化输出校验通过: intent={}", validated.chatResponse().intent());
            return validated.chatResponse();
        } catch (IllegalArgumentException e) {
            log.warn("结构化输出校验失败，尝试提取 naturalReply: {}", e.getMessage());
            String naturalReply = extractNaturalReply(content);
            if (naturalReply != null) {
                int estimatedTokens = AiServiceUtils.estimateTokens(userMessage) + AiServiceUtils.estimateTokens(naturalReply);
                return parseLlmOutput(naturalReply, userMessage, estimatedTokens);
            }
            int estimatedTokens = AiServiceUtils.estimateTokens(userMessage) + AiServiceUtils.estimateTokens(content);
            return parseLlmOutput(content, userMessage, estimatedTokens);
        }
    }

    /**
     * 通过 Spring AI StreamingChatModel 流式调用 DeepSeek 等 OpenAI 兼容 API。
     *
     * <p>每收到一个 Token 片段即通过 {@code onContentDelta} 回调推送，
     * 实现真正的“边生成边推送”流式效果，而非等待完整响应后再分块模拟。</p>
     */
    private ChatResponse realLlmStreamCall(String systemPrompt, List<ChatMessage> history,
                                           String userMessage, Consumer<String> onContentDelta) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt + outputFormatInstruction));
        for (ChatMessage msg : history) {
            messages.add(switch (msg.role()) {
                case USER -> new UserMessage(msg.content());
                case ASSISTANT -> new AssistantMessage(msg.content());
                case SYSTEM -> new SystemMessage(msg.content());
            });
        }
        messages.add(new UserMessage(userMessage));

        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.1)
                .build());

        // 使用流式 API：逐 Token 回调 onContentDelta
        StringBuilder contentBuilder = new StringBuilder();
        Flux<org.springframework.ai.chat.model.ChatResponse> flux = streamingChatModel.stream(prompt);
        flux.doOnNext(cr -> {
            if (cr.getResult() != null && cr.getResult().getOutput() != null
                    && cr.getResult().getOutput().getText() != null) {
                String token = cr.getResult().getOutput().getText();
                contentBuilder.append(token);
                onContentDelta.accept(token);
            }
        }).blockLast(); // 阻塞等待流完成，同时已逐步回调了 onContentDelta

        String content = contentBuilder.toString();
        if (content.isBlank()) {
            return fallbackResponse();
        }

        // 结构化校验（与 realLlmCall 相同逻辑）
        try {
            StructuredOutputValidator.ValidatedResponse validated = validator.validate(content);
            log.debug("流式结构化输出校验通过: intent={}", validated.chatResponse().intent());
            return validated.chatResponse();
        } catch (IllegalArgumentException e) {
            log.warn("流式结构化输出校验失败，尝试提取 naturalReply: {}", e.getMessage());
            String naturalReply = extractNaturalReply(content);
            if (naturalReply != null) {
                int estimatedTokens = AiServiceUtils.estimateTokens(userMessage) + AiServiceUtils.estimateTokens(naturalReply);
                return parseLlmOutput(naturalReply, userMessage, estimatedTokens);
            }
            int estimatedTokens = AiServiceUtils.estimateTokens(userMessage) + AiServiceUtils.estimateTokens(content);
            return parseLlmOutput(content, userMessage, estimatedTokens);
        }
    }

    private String extractNaturalReply(String text) {
        if (text == null || text.isBlank()) return null;
        var m = java.util.regex.Pattern.compile("\"naturalReply\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(text);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\n", "\n") : null;
    }

    /**
     * 纯自然语言流式调用，不附加结构化输出格式指令。
     *
     * <p>与 {@link #realLlmStreamCall} 相同，但 system prompt 不拼接
     * {@code outputFormatInstruction}，LLM 直接返回自然语言文本而非 JSON。</p>
     */
    private ChatResponse realLlmStreamCallPlain(String systemPrompt, List<ChatMessage> history,
                                                String userMessage, Consumer<String> onContentDelta) {
        List<Message> messages = new ArrayList<>();
        // 关键差异：不附加 outputFormatInstruction，LLM 直接输出自然语言
        messages.add(new SystemMessage(systemPrompt));
        for (ChatMessage msg : history) {
            messages.add(switch (msg.role()) {
                case USER -> new UserMessage(msg.content());
                case ASSISTANT -> new AssistantMessage(msg.content());
                case SYSTEM -> new SystemMessage(msg.content());
            });
        }
        messages.add(new UserMessage(userMessage));

        Prompt prompt = new Prompt(messages, OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.3)
                .build());

        StringBuilder contentBuilder = new StringBuilder();
        Flux<org.springframework.ai.chat.model.ChatResponse> flux = streamingChatModel.stream(prompt);
        flux.doOnNext(cr -> {
            if (cr.getResult() != null && cr.getResult().getOutput() != null
                    && cr.getResult().getOutput().getText() != null) {
                String token = cr.getResult().getOutput().getText();
                contentBuilder.append(token);
                onContentDelta.accept(token);
            }
        }).blockLast();

        String content = contentBuilder.toString();
        if (content.isBlank()) {
            return fallbackResponse();
        }
        int estimatedTokens = AiServiceUtils.estimateTokens(userMessage) + AiServiceUtils.estimateTokens(content);
        return new ChatResponse(content, IntentType.UNKNOWN, Map.of(), estimatedTokens, false);
    }

    // ---- 输出解析 ----

    private ChatResponse parseLlmOutput(String content, String originalInput, int tokens) {
        // 安全边界：意图关键词匹配必须基于用户原始输入，而非 LLM 回复文本。
        // LLM 回复中可能提及多种功能（如"你可以查余额、看交易记录"），
        // 若基于回复做关键词匹配会导致意图误判（用户说"你好"却被识别为交易查询）。
        String inputLower = originalInput.toLowerCase();
        String contentLower = content.toLowerCase();
        // 交易状态查询必须在转账之前检查，避免“转账状态”“转账处理中”被 TRANSFER 抢先匹配
        if (containsAny(inputLower, "交易状态", "转账状态", "处理中")) {
            Map<String, Object> slots = new java.util.HashMap<>();
            String txId = extractTransactionId(originalInput);
            if (txId != null && !txId.isBlank()) {
                slots.put("transactionId", txId);
            }
            return new ChatResponse(content, IntentType.TRANSACTION_STATUS, slots, tokens, slots.isEmpty());
        }
        if (containsAny(inputLower, "转账", "转给")) {
            Map<String, Object> slots = new java.util.HashMap<>();
            slots.put("amountFen", inferAmountFen(originalInput + " " + content));
            String payeeName = extractPayeeName(originalInput);
            if (payeeName != null && !payeeName.isBlank()) {
                slots.put("query", payeeName);
            }
            return new ChatResponse(content, IntentType.TRANSFER, slots, tokens, false);
        }
        // 账单查询必须在交易明细之前，避免"账单"被 TRANSACTION_LIST 抢先匹配
        if (containsAny(inputLower, "账单", "花呗账单", "本月账单")) {
            return new ChatResponse(content, IntentType.CREDIT_BILL, Map.of(), tokens, false);
        }
        // 交易明细必须在余额之前，避免用户输入同时包含"余额"和"交易记录"时误判
        if (containsAny(inputLower, "交易记录", "交易明细")) {
            // GAP-1：提取筛选参数（基于用户输入）
            Map<String, Object> slots = new java.util.HashMap<>();
            if (containsAny(inputLower, "支出", "花", "转出")) {
                slots.put("direction", "OUT");
            } else if (containsAny(inputLower, "收入", "收款", "转入")) {
                slots.put("direction", "IN");
            }
            return new ChatResponse(content, IntentType.TRANSACTION_LIST, slots, tokens, false);
        }
        if (containsAny(inputLower, "余额")) {
            return new ChatResponse(content, IntentType.BALANCE_QUERY, Map.of(), tokens, false);
        }
        // 交易状态已在转账之前检查，无需重复
        if (containsAny(inputLower, "收款人", "搜索", "找到")) {
            return new ChatResponse(content, IntentType.USER_SEARCH, Map.of(), tokens, true);
        }
        if (containsAny(inputLower, "花呗", "信用额度", "还款")) {
            if (containsAny(inputLower, "还款", "还")) {
                // PRD 要求：禁止模型默认选择"全部还清"
                boolean fullRepay = containsAny(inputLower, "全部还清", "全额还", "还清全部", "一次还清");
                return new ChatResponse(content, IntentType.CREDIT_REPAYMENT,
                        Map.of(), tokens, fullRepay);
            }
            return new ChatResponse(content, IntentType.CREDIT_SUMMARY, Map.of(), tokens, false);
        }
        // 澄清检测可基于 LLM 回复：当回复中包含"请提供""请告诉"等引导语时，
        // 说明 LLM 判断需要用户补充信息
        if (containsAny(contentLower, "?", "？", "请提供", "请告诉")) {
            return new ChatResponse(content, IntentType.UNKNOWN, Map.of(), tokens, true);
        }
        return new ChatResponse(content, IntentType.UNKNOWN, Map.of(), tokens, false);
    }

    // ---- Mock 降级 ----

    private ChatResponse mockLlmResponse(String userMessage) {
        String lower = userMessage.toLowerCase();
        if (containsAny(lower, "转账", "转给", "汇款", "转钱")) {
            // 检测是否包含金额信息（支持 "100元"、"两千"、"500块" 等格式）
            boolean hasAmount = lower.contains("元") || lower.contains("块")
                    || containsAny(lower, "金额", "多少")
                    || java.util.regex.Pattern.compile("[零一二三四五六七八九十百千万亿两]+[十百千万]?").matcher(lower).find();
            if (!hasAmount) {
                return new ChatResponse("好的，请告诉我收款人是谁，以及转账金额是多少？",
                        IntentType.TRANSFER, Map.of(), 30, true);
            }
            // 尝试从用户消息中提取收款人名称（"转给XXX" 或 "给XXX转"）
            String payeeName = extractPayeeName(userMessage);
            Map<String, Object> slots = new java.util.HashMap<>();
            slots.put("amountFen", inferAmountFen(lower));
            if (payeeName != null && !payeeName.isBlank()) {
                slots.put("query", payeeName);
            }
            return new ChatResponse("已为您查找收款人并确认金额。请核对信息后在确认卡片中点击确认。",
                    IntentType.TRANSFER, slots, 45, false);
        }
        if (containsAny(lower, "余额", "多少钱", "查余额")) {
            return new ChatResponse("您当前账户可用余额为 10,000.00 元。",
                    IntentType.BALANCE_QUERY, Map.of(), 25, false);
        }
        // 账单查询必须在交易明细之前，避免"账单"被 TRANSACTION_LIST 抢先匹配
        if (containsAny(lower, "账单", "花呗账单", "本月账单")) {
            return new ChatResponse("您本月的花呗账单如下：应还 0 元，未出账 0 元。",
                    IntentType.CREDIT_BILL, Map.of(), 30, false);
        }
        if (containsAny(lower, "交易记录", "交易明细", "流水")) {
            // GAP-1：提取筛选参数（方向、时间范围）
            Map<String, Object> slots = new java.util.HashMap<>();
            if (containsAny(lower, "支出", "花", "转出", "付出")) {
                slots.put("direction", "OUT");
            } else if (containsAny(lower, "收入", "收款", "转入", "入账")) {
                slots.put("direction", "IN");
            }
            return new ChatResponse("以下是您最近的交易明细……需要查看更多吗？",
                    IntentType.TRANSACTION_LIST, slots, 35, false);
        }
        if (containsAny(lower, "交易状态", "转到哪了")) {
            // 尝试提取交易编号
            String txId = extractTransactionId(userMessage);
            if (txId != null && !txId.isBlank()) {
                Map<String, Object> slots = new java.util.HashMap<>();
                slots.put("transactionId", txId);
                return new ChatResponse("正在为您查询该笔交易的状态。",
                        IntentType.TRANSACTION_STATUS, slots, 25, false);
            }
            return new ChatResponse("请提供您要查询的交易编号（可在交易记录中查看）。",
                    IntentType.TRANSACTION_STATUS, Map.of(), 20, true);
        }
        if (containsAny(lower, "找", "搜索", "收款人")) {
            return new ChatResponse("请告诉我您要搜索的收款人手机号（11 位）。",
                    IntentType.USER_SEARCH, Map.of(), 18, true);
        }
        if (containsAny(lower, "花呗", "信用", "额度")) {
            if (containsAny(lower, "还", "还款")) {
                // PRD 要求：禁止模型默认选择"全部还清"，必须要求用户指定具体金额
                if (containsAny(lower, "全部还清", "全额还", "还清全部", "一次还清")) {
                    return new ChatResponse(
                            "抱歉，暂不支持全部还清功能。请告诉我您想还款的具体金额（如'还200元'）。",
                            IntentType.CREDIT_REPAYMENT, Map.of(), 30, true);
                }
                return new ChatResponse("您的花呗待还总额为 0 元。请问要还多少？",
                        IntentType.CREDIT_REPAYMENT, Map.of(), 28, true);
            }
            return new ChatResponse("您的 Mini 花呗总额度 5,000.00 元，已用 0 元。",
                    IntentType.CREDIT_SUMMARY, Map.of(), 28, false);
        }
        return new ChatResponse("抱歉，我没有理解您的意图。我可以帮您：转账、查余额、查交易明细、查花呗账单、还花呗、搜收款人。",
                IntentType.UNKNOWN, Map.of(), 40, true);
    }

    private ChatResponse fallbackResponse() {
        return new ChatResponse("抱歉，AI 服务暂时繁忙，请稍后重试或选择手动操作表单。",
                IntentType.UNKNOWN, Map.of(), 15, true);
    }

    // ---- 工具方法 ----

    /**
     * 默认的结构化输出格式指令，当 application.yml 未配置 ai.prompt.output-format 时使用。
     */
    private static String getDefaultOutputFormatInstruction() {
        return """

                ---
                你必须使用以下 JSON 格式回复，不要包含任何 JSON 之外的内容：
                {
                  "intent": "TRANSFER|BALANCE_QUERY|TRANSACTION_LIST|TRANSACTION_STATUS|USER_SEARCH|CREDIT_SUMMARY|CREDIT_BILL|CREDIT_REPAYMENT|UNKNOWN",
                  "slots": {"槽位名": "槽位值"},
                  "missingFields": ["缺失的必填字段"],
                  "confidence": 0.0到1.0之间的数值,
                  "clarificationNeeded": true或false,
                  "naturalReply": "面向用户展示的自然语言回复"
                }
                规则：
                - intent 必须是枚举值之一，无法确定时用 UNKNOWN
                - 缺失必填字段时 clarificationNeeded=true，missingFields 列出字段名
                - amountFen 必须是整数（分），例如 10000 表示 100.00 元
                - payeeId 留 null，由工具查询后填入
                - 转账时必须将用户提到的收款人名称/手机号提取到 query 槽位，供搜索工具使用
                - naturalReply 是给用户看的自然语言回复
                - 不要编造任何金额、账户或交易状态
                """;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) { if (text.contains(kw)) return true; }
        return false;
    }

    private long inferAmountFen(String text) {
        // 先尝试匹配阿拉伯数字 + 元/块
        var m = java.util.regex.Pattern.compile("(\\d+)\\s*[元块]?").matcher(text);
        if (m.find()) {
            return Long.parseLong(m.group(1)) * 100;
        }
        // 再尝试匹配中文数字（如 "两千"、"五百"、"一万"）
        Map<String, Long> cnDigits = Map.ofEntries(
                Map.entry("零", 0L), Map.entry("一", 1L), Map.entry("二", 2L),
                Map.entry("两", 2L), Map.entry("三", 3L), Map.entry("四", 4L),
                Map.entry("五", 5L), Map.entry("六", 6L), Map.entry("七", 7L),
                Map.entry("八", 8L), Map.entry("九", 9L), Map.entry("十", 10L)
        );
        Map<String, Long> cnUnits = Map.of("十", 10L, "百", 100L, "千", 1000L, "万", 10000L);
        var cnMatcher = java.util.regex.Pattern.compile("([零一二三四五六七八九十百千万亿两]+)").matcher(text);
        if (cnMatcher.find()) {
            String cnNum = cnMatcher.group(1);
            long result = 0;
            long current = 0;
            for (int i = 0; i < cnNum.length(); i++) {
                String ch = String.valueOf(cnNum.charAt(i));
                if (cnDigits.containsKey(ch)) {
                    current = cnDigits.get(ch);
                } else if (cnUnits.containsKey(ch)) {
                    long unit = cnUnits.get(ch);
                    if (current == 0 && unit == 10) {
                        current = 1; // "十" 单独出现表示 10
                    }
                    result += current * unit;
                    current = 0;
                }
            }
            result += current;
            if (result > 0) {
                return result * 100;
            }
        }
        return 0L;
    }

    /**
     * 从用户消息中提取收款人信息。
     * 优先提取 11 位手机号，其次提取中文姓名。
     * 支持模式："转给13800138000"、"给张三转100元"、"转给13800138000转100元"。
     */
    private String extractPayeeName(String text) {
        if (text == null || text.isBlank()) return null;

        // 优先匹配 11 位手机号（1 开头），手机号不应被数字过滤
        var phoneMatcher = java.util.regex.Pattern.compile("1[3-9]\\d{9}").matcher(text);
        if (phoneMatcher.find()) {
            return phoneMatcher.group();
        }

        // 匹配 "转给XXX" 或 "给XXX转/汇/发"，XXX 为中文姓名（2-10 字）
        var m = java.util.regex.Pattern.compile("[转汇发]?给([^，,。转汇发\\d]{2,10})(?:[，,。]|转|汇|发|$)").matcher(text);
        if (m.find()) {
            String name = m.group(1).trim();
            if (!name.isBlank() && name.length() <= 10) {
                return name;
            }
        }
        return null;
    }

    /**
     * 从用户消息中提取交易编号。
     * 支持模式：纯字母数字组合且长度 >= 8 的字符串（如 ULID 格式）。
     */
    private String extractTransactionId(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        // 匹配 ULID 或类似格式的交易编号（26 位大写字母+数字）
        var m = java.util.regex.Pattern.compile("\\b([0-9A-Z]{20,30})\\b").matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        // 退而求其次：匹配较长的纯数字/字母串（>= 8 位）
        var fallback = java.util.regex.Pattern.compile("\\b([0-9a-zA-Z]{8,})\\b").matcher(text);
        if (fallback.find()) {
            return fallback.group(1);
        }
        return null;
    }

    private static long parseDurationSeconds(String d) {
        return parseMillis(d) / 1000;
    }

    private static long parseMillis(String d) {
        String t = d.trim().toLowerCase();
        if (t.endsWith("ms")) return Long.parseLong(t.replace("ms", ""));
        if (t.endsWith("s")) return Long.parseLong(t.replace("s", "")) * 1000;
        if (t.endsWith("m")) return Long.parseLong(t.replace("m", "")) * 60000;
        return Long.parseLong(t);
    }

    // ---- 熔断器 ----

    static class CircuitBreaker {
        private final int threshold;
        private final long openSec;
        private final AtomicInteger failures = new AtomicInteger(0);
        private volatile long openUntil;
        private volatile boolean halfOpen;

        CircuitBreaker(int threshold, long openSec) {
            this.threshold = threshold;
            this.openSec = openSec;
        }

        void assertNotOpen() {
            long now = System.currentTimeMillis() / 1000;
            if (openUntil > 0 && now < openUntil)
                throw new BusinessException(AgentErrorCode.LLM_UNAVAILABLE);
            if (openUntil > 0 && now >= openUntil && !halfOpen) {
                halfOpen = true;
                log.info("熔断器进入 HALF_OPEN");
            }
        }

        void recordSuccess() {
            failures.set(0);
            if (halfOpen) { halfOpen = false; openUntil = 0; log.info("熔断器恢复 CLOSED"); }
        }

        void recordFailure() {
            int f = failures.incrementAndGet();
            if (halfOpen) {
                openUntil = System.currentTimeMillis() / 1000 + openSec;
                halfOpen = false;
                log.warn("HALF_OPEN 探测失败，重新 OPEN");
                return;
            }
            if (f >= threshold) {
                openUntil = System.currentTimeMillis() / 1000 + openSec;
                failures.set(0);
                log.warn("连续失败 {} 次，熔断器 OPEN", f);
            }
        }
    }
}
