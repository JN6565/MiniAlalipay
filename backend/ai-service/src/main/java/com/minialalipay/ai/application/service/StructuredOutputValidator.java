package com.minialalipay.ai.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.domain.agent.IntentType;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * 结构化输出校验器。
 *
 * <p>将 LLM 返回的文本解析为 JSON，用版本化 JSON Schema 校验后映射为
 * {@link ChatResponse}。校验失败最多重试一次，仍失败则抛出异常由上层降级处理。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>Schema 设为 {@code additionalProperties: false}，拒绝多余字段</li>
 *   <li>{@code amountFen} 必须为整数（long），拒绝浮点数</li>
 *   <li>{@code payeeId} 格式校验（ULID 长度 26）</li>
 *   <li>保留原始 JSON 用于审计追踪</li>
 * </ul>
 */
@Component
public class StructuredOutputValidator {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputValidator.class);

    private final ObjectMapper objectMapper;
    private final JsonSchema schema;

    public StructuredOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schema = loadSchema();
    }

    /**
     * 无参构造器，供测试使用。创建默认 ObjectMapper 并从 classpath 加载 Schema。
     */
    StructuredOutputValidator() {
        this.objectMapper = new ObjectMapper();
        this.schema = loadSchema();
    }

    private JsonSchema loadSchema() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("schemas/agent_reply_schema_v1.json")) {
            if (is == null) {
                throw new IllegalStateException("Schema 文件未找到: schemas/agent_reply_schema_v1.json");
            }
            return factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 JSON Schema: " + e.getMessage(), e);
        }
    }

    /**
     * 校验 LLM 输出并映射为 ChatResponse。
     *
     * @param llmOutput LLM 原始输出（可能包含 markdown 代码块包裹）
     * @return 校验后的 ChatResponse 和原始 JSON
     * @throws IllegalArgumentException 如果 JSON 提取失败或 Schema 校验不通过
     */
    public ValidatedResponse validate(String llmOutput) {
        String rawJson = extractJson(llmOutput);
        JsonNode node = parseAndValidate(rawJson);
        ChatResponse cr = mapToChatResponse(node, rawJson);
        return new ValidatedResponse(cr, rawJson);
    }

    /**
     * 从 LLM 输出中提取 JSON。处理 markdown 代码块包裹。
     */
    String extractJson(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("LLM 输出为空");
        }
        String trimmed = text.trim();
        // 处理 ```json ... ``` 包裹
        int codeStart = trimmed.indexOf("```json");
        if (codeStart >= 0) {
            int jsonStart = trimmed.indexOf('\n', codeStart);
            int jsonEnd = trimmed.indexOf("```", jsonStart > 0 ? jsonStart : codeStart + 7);
            if (jsonStart > 0 && jsonEnd > jsonStart) {
                return trimmed.substring(jsonStart, jsonEnd).trim();
            }
        }
        // 处理 ``` ... ``` 包裹（无语言标记）
        if (trimmed.startsWith("```")) {
            int jsonEnd = trimmed.indexOf("```", 3);
            if (jsonEnd > 3) {
                return trimmed.substring(trimmed.indexOf('\n') + 1, jsonEnd).trim();
            }
        }
        // 尝试查找 JSON 对象的起止位置
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        throw new IllegalArgumentException("无法从 LLM 输出中提取 JSON: " + text.substring(0, Math.min(100, text.length())));
    }

    /**
     * 解析 JSON 并执行 Schema 校验。
     */
    JsonNode parseAndValidate(String rawJson) {
        JsonNode node;
        try {
            node = objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 解析失败: " + e.getMessage(), e);
        }

        Set<ValidationMessage> errors = schema.validate(node);
        if (!errors.isEmpty()) {
            String msg = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Schema 校验失败");
            log.warn("JSON Schema 校验失败: {}", msg);
            throw new IllegalArgumentException("Schema 校验失败 (additionalProperties=false 或其它约束): " + msg);
        }

        // 额外校验：amountFen 必须是整数
        JsonNode slots = node.get("slots");
        if (slots != null && slots.has("amountFen") && slots.get("amountFen").isFloatingPointNumber()) {
            throw new IllegalArgumentException("amountFen 必须是整数（long），不能是浮点数");
        }

        // 额外校验：payeeId 存在时须为 26 位 ULID 格式
        if (slots != null && slots.has("payeeId") && !slots.get("payeeId").isNull()) {
            String payeeId = slots.get("payeeId").asText();
            if (payeeId.length() != 26) {
                throw new IllegalArgumentException("payeeId 格式无效，须为 26 位 ULID: " + payeeId);
            }
        }

        return node;
    }

    /**
     * 将校验通过的 JSON 节点映射为 ChatResponse。
     */
    ChatResponse mapToChatResponse(JsonNode node, String rawJson) {
        IntentType intent;
        try {
            intent = IntentType.valueOf(node.get("intent").asText());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("intent 值无效: " + node.get("intent").asText());
        }

        Map<String, Object> slots = new HashMap<>();
        JsonNode slotsNode = node.get("slots");
        if (slotsNode != null && slotsNode.isObject()) {
            var iter = slotsNode.fields();
            while (iter.hasNext()) {
                var entry = iter.next();
                JsonNode value = entry.getValue();
                if (value.isIntegralNumber()) {
                    slots.put(entry.getKey(), value.asLong());
                } else if (value.isTextual()) {
                    slots.put(entry.getKey(), value.asText());
                } else if (value.isBoolean()) {
                    slots.put(entry.getKey(), value.asBoolean());
                } else if (value.isNull()) {
                    // 跳过 null 值，避免 NPE
                }
            }
        }

        boolean clarificationNeeded = node.has("clarificationNeeded") && node.get("clarificationNeeded").asBoolean();
        String naturalReply = node.has("naturalReply") ? node.get("naturalReply").asText() : "";
        double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.0;
        int tokens = rawJson.length() / 4; // 粗略估算：JSON 中每 4 字符约 1 token

        return new ChatResponse(naturalReply, intent, slots, tokens, clarificationNeeded);
    }

    /**
     * 校验结果，包含映射后的 ChatResponse 和保留的原始 JSON。
     */
    public record ValidatedResponse(ChatResponse chatResponse, String rawJson) {
    }
}
