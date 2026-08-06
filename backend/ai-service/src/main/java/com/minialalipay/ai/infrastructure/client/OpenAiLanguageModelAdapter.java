package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.application.service.StructuredOutputValidator;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.domain.agent.IntentType;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final String OUTPUT_FORMAT_INSTRUCTION = """

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
            - intent 必须是枚举值之一
            - amountFen 必须是整数（分），例如 10000 表示 100.00 元
            - payeeId 留 null，由工具查询后填入
            - 缺失必填字段时 clarificationNeeded=true，missingFields 列出字段名
            - naturalReply 是给用户看的自然语言回复
            - 不要编造任何金额、账户或交易状态
            """;

    private final ChatModel chatModel;
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
            ObjectProvider<ChatModel> chatModelProvider,
            StructuredOutputValidator validator
    ) {
        this.model = model;
        // API Key 以 "sk-" 开头 → 真实模式；否则 → Mock 模式
        this.mockMode = apiKey == null || apiKey.isBlank() || !apiKey.startsWith("sk-");
        // mock 模式下不获取 ChatModel（Spring AI 必须删除了 ChatAutoConfiguration 排除才能创建该 Bean）
        this.chatModel = mockMode ? null : chatModelProvider.getIfAvailable();
        this.semaphore = new Semaphore(maxConcurrent);
        this.circuitBreaker = new CircuitBreaker(failureThreshold, parseDurationSeconds(openStateDuration));
        this.validator = validator;
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

    // ---- 真实 LLM 调用 ----

    /**
     * 通过 Spring AI ChatModel 调用 DeepSeek 等 OpenAI 兼容 API。
     */
    private ChatResponse realLlmCall(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
        // 在 System Prompt 末尾追加结构化输出格式指令
        messages.add(new SystemMessage(systemPrompt + OUTPUT_FORMAT_INSTRUCTION));
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

        // 尝试结构化校验
        try {
            StructuredOutputValidator.ValidatedResponse validated = validator.validate(content);
            log.debug("结构化输出校验通过: intent={}, confidence={}",
                    validated.chatResponse().intent(), content.length());
            return validated.chatResponse();
        } catch (IllegalArgumentException e) {
            log.warn("结构化输出校验失败，回退到关键词匹配: {}", e.getMessage());
            // 校验失败回退到关键词匹配（保持兼容）
            int estimatedTokens = estimateTokens(userMessage) + estimateTokens(content);
            return parseLlmOutput(content, userMessage, estimatedTokens);
        }
    }

    // ---- 输出解析 ----

    /**
     * 从真实 LLM 回复中解析意图。仅从用户原文提取关键词匹配，不从 LLM 回复中匹配
     *（LLM 回复会自然提及能力范围如"我可以帮你转账…"，导致误判）。
     */
    private ChatResponse parseLlmOutput(String content, String originalInput, int tokens) {
        String lower = originalInput.toLowerCase();
        if (containsAny(lower, "转账", "转给", "汇款", "转钱")) {
            long amount = inferAmountFen(lower);
            boolean hasAmount = amount > 0;
            return new ChatResponse(content, IntentType.TRANSFER,
                    Map.of("amountFen", amount), tokens, !hasAmount);
        }
        if (containsAny(lower, "余额", "多少钱", "查余额")) {
            return new ChatResponse(content, IntentType.BALANCE_QUERY, Map.of(), tokens, false);
        }
        if (containsAny(lower, "交易记录", "交易明细", "流水")) {
            return new ChatResponse(content, IntentType.TRANSACTION_LIST, Map.of(), tokens, false);
        }
        if (containsAny(lower, "交易状态", "转到哪了")) {
            return new ChatResponse(content, IntentType.TRANSACTION_STATUS, Map.of(), tokens, true);
        }
        if (containsAny(lower, "找", "搜索", "收款人")) {
            return new ChatResponse(content, IntentType.USER_SEARCH, Map.of(), tokens, true);
        }
        if (containsAny(lower, "花呗", "信用", "额度")) {
            boolean isRepay = containsAny(lower, "还", "还款");
            return new ChatResponse(content,
                    isRepay ? IntentType.CREDIT_REPAYMENT : IntentType.CREDIT_SUMMARY,
                    Map.of(), tokens, isRepay);
        }
        // 未匹配任何关键词 → LLM 生成自然回复即可
        return new ChatResponse(content, IntentType.UNKNOWN, Map.of(), tokens, true);
    }

    // ---- Mock 降级 ----

    private ChatResponse mockLlmResponse(String userMessage) {
        String lower = userMessage.toLowerCase();
        String json;
        if (containsAny(lower, "转账", "转给", "汇款", "转钱")) {
            if (!lower.contains("元") && !containsAny(lower, "金额", "多少")) {
                json = """
                        {"intent":"TRANSFER","slots":{},"missingFields":["payeeId","amountFen"],"confidence":0.3,"clarificationNeeded":true,"naturalReply":"好的，请告诉我收款人是谁，以及转账金额是多少？"}""";
            } else {
                long amount = inferAmountFen(lower);
                json = "{\"intent\":\"TRANSFER\",\"slots\":{\"amountFen\":" + amount + "},\"missingFields\":[\"payeeId\"],\"confidence\":0.6,\"clarificationNeeded\":true,\"naturalReply\":\"已记录金额，请告诉我收款人是谁？\"}";
            }
        } else if (containsAny(lower, "余额", "多少钱", "查余额")) {
            json = """
                    {"intent":"BALANCE_QUERY","slots":{},"missingFields":[],"confidence":0.9,"clarificationNeeded":false,"naturalReply":"正在为您查询余额…"}""";
        } else if (containsAny(lower, "交易记录", "交易明细", "流水")) {
            json = """
                    {"intent":"TRANSACTION_LIST","slots":{},"missingFields":[],"confidence":0.9,"clarificationNeeded":false,"naturalReply":"正在为您查询交易明细…"}""";
        } else if (containsAny(lower, "交易状态", "转到哪了")) {
            json = """
                    {"intent":"TRANSACTION_STATUS","slots":{},"missingFields":["transactionId"],"confidence":0.4,"clarificationNeeded":true,"naturalReply":"请提供您要查询的交易编号。"}""";
        } else if (containsAny(lower, "找", "搜索", "收款人")) {
            json = """
                    {"intent":"USER_SEARCH","slots":{},"missingFields":["query"],"confidence":0.3,"clarificationNeeded":true,"naturalReply":"请告诉我您要搜索的收款人姓名或手机号尾号。"}""";
        } else if (containsAny(lower, "花呗", "信用", "额度")) {
            if (containsAny(lower, "还", "还款")) {
                json = """
                        {"intent":"CREDIT_REPAYMENT","slots":{},"missingFields":["amountFen"],"confidence":0.5,"clarificationNeeded":true,"naturalReply":"您的花呗待还总额为 0 元。请问要还多少？"}""";
            } else {
                json = """
                        {"intent":"CREDIT_SUMMARY","slots":{},"missingFields":[],"confidence":0.9,"clarificationNeeded":false,"naturalReply":"正在为您查询花呗额度…"}""";
            }
        } else {
            json = """
                    {"intent":"UNKNOWN","slots":{},"missingFields":[],"confidence":0.0,"clarificationNeeded":true,"naturalReply":"抱歉，我没有理解您的意图。我可以帮您：转账、查余额、查交易、查花呗、还花呗。"}""";
        }
        // 通过结构化校验（Mock 模式也走同一校验通道）
        return validator.validate(json).chatResponse();
    }

    private ChatResponse fallbackResponse() {
        return new ChatResponse("抱歉，AI 服务暂时繁忙，请稍后重试或选择手动操作表单。",
                IntentType.UNKNOWN, Map.of(), 15, true);
    }

    // ---- 工具方法 ----

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) { if (text.contains(kw)) return true; }
        return false;
    }

    private long inferAmountFen(String text) {
        var m = java.util.regex.Pattern.compile("(\\d+)\\s*元").matcher(text);
        return m.find() ? Long.parseLong(m.group(1)) * 100 : 0L;
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
