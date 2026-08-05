package com.minialalipay.ai.application.security;

import com.minialalipay.ai.domain.tool.ToolCallLog;
import com.minialalipay.ai.domain.tool.ToolCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * 工具调用审计服务。
 *
 * <p>每次工具调用记录脱敏摘要和标准结果码到 tool_call_log 表，
 * 并通过结构化日志输出审计记录。不保存原始参数、密码或令牌。</p>
 */
@Service
public class ToolAuditService {

    private static final Logger auditLog = LoggerFactory.getLogger("AI_TOOL_AUDIT");

    private final ToolCallLogRepository toolCallLogRepository;

    public ToolAuditService(ToolCallLogRepository toolCallLogRepository) {
        this.toolCallLogRepository = toolCallLogRepository;
    }

    /**
     * 记录工具调用审计日志。
     *
     * @param toolName 工具契约名称
     * @param params 已脱敏的调用参数
     * @param resultCode 标准工具结果码
     * @param sessionId 发起调用的会话 ID
     * @param traceId 跨服务 Trace ID
     * @param durationMs 调用耗时（毫秒）
     * @param occurredAt 调用发生时间
     */
    public void audit(String toolName, Map<String, Object> params,
                      String resultCode, String sessionId, String traceId,
                      int durationMs, Instant occurredAt) {
        byte[] digest = computeDigest(normalizeParams(params));

        ToolCallLog log = new ToolCallLog(
                generateUlid(), sessionId, toolName,
                digest, resultCode, durationMs, traceId, occurredAt);
        toolCallLogRepository.insert(log);

        // 结构化审计日志：不记录原始参数
        auditLog.info("工具调用: tool={}, resultCode={}, sessionId={}, durationMs={}, traceId={}",
                toolName, resultCode, sessionId, durationMs, traceId);
    }

    private byte[] computeDigest(String normalizedJson) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(normalizedJson.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String normalizeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "{}";
        // 简单的 JSON 规范化：按 key 排序
        StringBuilder sb = new StringBuilder("{");
        params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append("\"").append(e.getKey()).append("\":\"")
                        .append(e.getValue()).append("\","));
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("}");
        return sb.toString();
    }

    private static String generateUlid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 26);
    }
}
