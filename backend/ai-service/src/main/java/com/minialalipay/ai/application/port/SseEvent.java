package com.minialalipay.ai.application.port;

import java.util.List;
import java.util.Map;

/**
 * SSE 事件类型与载荷定义。
 *
 * <p>所有事件载荷均为记录类型，可直接序列化为 JSON 放入 SSE data 行。
 * 遵循系统分析 §12.6 的安全约束：不传输支付密码、确认令牌或完整账号。</p>
 */
public final class SseEvent {

    private SseEvent() { /* 工具类 */ }

    /** 事件类型，对应 SSE event: 行 */
    public enum Type {
        AGENT_STATUS,
        AGENT_TOOL_CALL,
        AGENT_TOOL_RESULT,
        AGENT_CONTENT,
        AGENT_CLARIFICATION,
        AGENT_DONE,
        AGENT_ERROR
    }

    public record StatusPayload(String stage, String message) {}

    public record ToolCallPayload(String tool, String status) {}

    public record ToolResultPayload(String tool, String status, String summary, java.util.Map<String, Object> data) {}

    public record ContentPayload(String delta) {}

    public record ClarificationPayload(
            String question,
            List<Option> options
    ) {}

    public record Option(String id, String label) {}

    public record DonePayload(String messageId, String sessionId, String intent) {}

    public record ErrorPayload(String code, String message) {}
}
