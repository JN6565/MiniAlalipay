package com.minialalipay.ai.infrastructure.persistence.po;

import java.time.Instant;

/**
 * 工具调用日志持久化对象，对应 {@code agent_db.tool_call_log} 表。
 *
 * <p>只保存规范化请求摘要和标准结果码，不保存原始支付密码、令牌或资金敏感响应。
 * trace_id 关联跨服务调用链路。</p>
 */
public class ToolCallLogPO {

    /** 工具调用 ID，对应 CHAR(26) */
    private String toolCallId;

    /** 发起调用的会话 ID，对应 CHAR(26) FK */
    private String sessionId;

    /** 工具契约名称，对应 VARCHAR(64) */
    private String toolName;

    /** 脱敏规范化请求摘要，对应 BINARY(32) */
    private byte[] requestDigest;

    /** 标准工具结果码，对应 VARCHAR(32) */
    private String resultCode;

    /** 调用耗时毫秒，对应 INT UNSIGNED */
    private Integer durationMs;

    /** 跨服务 Trace ID，对应 CHAR(32) */
    private String traceId;

    /** 调用发生时间，对应 DATETIME(3) */
    private Instant occurredAt;

    public ToolCallLogPO() {
    }

    public ToolCallLogPO(String toolCallId, String sessionId, String toolName,
                         byte[] requestDigest, String resultCode, Integer durationMs,
                         String traceId, Instant occurredAt) {
        this.toolCallId = toolCallId;
        this.sessionId = sessionId;
        this.toolName = toolName;
        this.requestDigest = requestDigest;
        this.resultCode = resultCode;
        this.durationMs = durationMs;
        this.traceId = traceId;
        this.occurredAt = occurredAt;
    }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public byte[] getRequestDigest() { return requestDigest; }
    public void setRequestDigest(byte[] requestDigest) { this.requestDigest = requestDigest; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public Integer getDurationMs() { return durationMs; }
    public void setDurationMs(Integer durationMs) { this.durationMs = durationMs; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
