package com.minialalipay.ai.domain.tool;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * 工具调用日志实体。
 *
 * <p>保存 AI/MCP 工具调用的脱敏摘要、标准结果码、耗时和 Trace 证据。
 * 不保存原始请求参数、支付密码、令牌或敏感响应内容。</p>
 *
 * <h3>关键不变量</h3>
 * <ul>
 *   <li>{@code request_digest} 为脱敏规范化请求的 HMAC-SHA-256 摘要（BINARY(32)）</li>
 *   <li>{@code result_code} 为标准工具结果码，使用稳定中文错误码</li>
 *   <li>{@code trace_id} 关联跨服务调用链路</li>
 * </ul>
 */
public class ToolCallLog {

    private final String toolCallId;
    private final String sessionId;
    private final String toolName;
    private final byte[] requestDigest;
    private final String resultCode;
    private final int durationMs;
    private final String traceId;
    private final Instant occurredAt;

    /**
     * 创建工具调用日志。
     *
     * @param toolCallId 工具调用 ID（ULID）
     * @param sessionId 发起调用的 AI 会话 ID
     * @param toolName 工具契约名称
     * @param requestDigest 脱敏规范化请求摘要（32 字节）
     * @param resultCode 标准工具结果码
     * @param durationMs 调用耗时（毫秒）
     * @param traceId 跨服务 Trace ID（32 字符）
     * @param occurredAt 调用发生时间
     */
    public ToolCallLog(
            String toolCallId, String sessionId, String toolName,
            byte[] requestDigest, String resultCode, int durationMs,
            String traceId, Instant occurredAt
    ) {
        this.toolCallId = Objects.requireNonNull(toolCallId, "工具调用 ID 不能为空");
        this.sessionId = Objects.requireNonNull(sessionId, "会话 ID 不能为空");
        this.toolName = Objects.requireNonNull(toolName, "工具名不能为空");
        this.requestDigest = Objects.requireNonNull(requestDigest, "请求摘要不能为空");
        if (requestDigest.length != 32) {
            throw new IllegalArgumentException("请求摘要必须为 32 字节");
        }
        this.resultCode = Objects.requireNonNull(resultCode, "结果码不能为空");
        if (durationMs < 0) {
            throw new IllegalArgumentException("耗时不能为负");
        }
        this.durationMs = durationMs;
        this.traceId = Objects.requireNonNull(traceId, "Trace ID 不能为空");
        this.occurredAt = Objects.requireNonNull(occurredAt, "发生时间不能为空");
    }

    // ---- getters ----

    public String getToolCallId() { return toolCallId; }
    public String getSessionId() { return sessionId; }
    public String getToolName() { return toolName; }

    /** @return 请求摘要的防御性副本 */
    public byte[] getRequestDigest() { return Arrays.copyOf(requestDigest, requestDigest.length); }
    public String getResultCode() { return resultCode; }
    public int getDurationMs() { return durationMs; }
    public String getTraceId() { return traceId; }
    public Instant getOccurredAt() { return occurredAt; }
}
