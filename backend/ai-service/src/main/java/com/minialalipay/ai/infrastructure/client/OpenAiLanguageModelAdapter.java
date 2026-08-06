package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.ChatMessage;
import com.minialalipay.ai.application.port.ChatResponse;
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

    private final ChatModel chatModel;
    private final String model;
    private final Semaphore semaphore;
    private final CircuitBreaker circuitBreaker;
    private final boolean mockMode;

    public OpenAiLanguageModelAdapter(
            @Value("${spring.ai.openai.api-key:}") String apiKey,
            @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String model,
            @Value("${ai.llm.max-concurrent:20}") int maxConcurrent,
            @Value("${ai.llm.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${ai.llm.circuit-breaker.open-state-duration:30s}") String openStateDuration,
            ObjectProvider<ChatModel> chatModelProvider
    ) {
        this.model = model;
        // API Key 以 "sk-" 开头 → 真实模式；否则 → Mock 模式
        this.mockMode = apiKey == null || apiKey.isBlank() || !apiKey.startsWith("sk-");
        // mock 模式下不获取 ChatModel（Spring AI 必须删除了 ChatAutoConfiguration 排除才能创建该 Bean）
        this.chatModel = mockMode ? null : chatModelProvider.getIfAvailable();
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

    // ---- 真实 LLM 调用 ----

    /**
     * 通过 Spring AI ChatModel 调用 DeepSeek 等 OpenAI 兼容 API。
     */
    private ChatResponse realLlmCall(String systemPrompt, List<ChatMessage> history, String userMessage) {
        List<Message> messages = new ArrayList<>();
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
                .temperature(0.1)
                .build());

        String content = chatModel.call(prompt).getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            return fallbackResponse();
        }

        // 简单估算 token 数（中文约 0.5 token/字）
        int estimatedTokens = estimateTokens(userMessage) + estimateTokens(content);

        return parseLlmOutput(content, userMessage, estimatedTokens);
    }

    // ---- 输出解析 ----

    private ChatResponse parseLlmOutput(String content, String originalInput, int tokens) {
        String lower = content.toLowerCase();
        if (containsAny(lower, "转账", "转给")) {
            return new ChatResponse(content, IntentType.TRANSFER,
                    Map.of("amountFen", inferAmountFen(originalInput + " " + content)), tokens, false);
        }
        if (containsAny(lower, "余额")) {
            return new ChatResponse(content, IntentType.BALANCE_QUERY, Map.of(), tokens, false);
        }
        if (containsAny(lower, "交易记录", "交易明细", "账单")) {
            return new ChatResponse(content, IntentType.TRANSACTION_LIST, Map.of(), tokens, false);
        }
        if (containsAny(lower, "交易状态", "处理中")) {
            return new ChatResponse(content, IntentType.TRANSACTION_STATUS, Map.of(), tokens, false);
        }
        if (containsAny(lower, "收款人", "搜索", "找到")) {
            return new ChatResponse(content, IntentType.USER_SEARCH, Map.of(), tokens, true);
        }
        if (containsAny(lower, "花呗", "信用额度", "还款")) {
            return new ChatResponse(content,
                    containsAny(lower, "还款", "还") ? IntentType.CREDIT_REPAYMENT : IntentType.CREDIT_SUMMARY,
                    Map.of(), tokens, false);
        }
        if (containsAny(lower, "?", "？", "请提供", "请告诉")) {
            return new ChatResponse(content, IntentType.UNKNOWN, Map.of(), tokens, true);
        }
        return new ChatResponse(content, IntentType.UNKNOWN, Map.of(), tokens, false);
    }

    // ---- Mock 降级 ----

    private ChatResponse mockLlmResponse(String userMessage) {
        String lower = userMessage.toLowerCase();
        if (containsAny(lower, "转账", "转给", "汇款", "转钱")) {
            if (!lower.contains("元") && !containsAny(lower, "金额", "多少")) {
                return new ChatResponse("好的，请告诉我收款人是谁，以及转账金额是多少？",
                        IntentType.TRANSFER, Map.of(), 30, true);
            }
            return new ChatResponse("已为您查找收款人并确认金额。请核对信息后在确认卡片中点击确认。",
                    IntentType.TRANSFER, Map.of("amountFen", inferAmountFen(lower)), 45, false);
        }
        if (containsAny(lower, "余额", "多少钱", "查余额")) {
            return new ChatResponse("您当前账户可用余额为 10,000.00 元。",
                    IntentType.BALANCE_QUERY, Map.of(), 25, false);
        }
        if (containsAny(lower, "交易记录", "交易明细", "流水")) {
            return new ChatResponse("以下是您最近的交易明细……需要查看更多吗？",
                    IntentType.TRANSACTION_LIST, Map.of(), 35, false);
        }
        if (containsAny(lower, "交易状态", "转到哪了")) {
            return new ChatResponse("请提供您要查询的交易编号。",
                    IntentType.TRANSACTION_STATUS, Map.of(), 20, true);
        }
        if (containsAny(lower, "找", "搜索", "收款人")) {
            return new ChatResponse("请告诉我您要搜索的收款人手机号（11 位）。",
                    IntentType.USER_SEARCH, Map.of(), 18, true);
        }
        if (containsAny(lower, "花呗", "信用", "额度")) {
            if (containsAny(lower, "还", "还款")) {
                return new ChatResponse("您的花呗待还总额为 0 元。请问要还多少？",
                        IntentType.CREDIT_REPAYMENT, Map.of(), 28, true);
            }
            return new ChatResponse("您的 Mini 花呗总额度 5,000.00 元，已用 0 元。",
                    IntentType.CREDIT_SUMMARY, Map.of(), 28, false);
        }
        return new ChatResponse("抱歉，我没有理解您的意图。我可以帮您：转账、查余额、查交易、查花呗、还花呗。",
                IntentType.UNKNOWN, Map.of(), 40, true);
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
