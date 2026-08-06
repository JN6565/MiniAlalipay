package com.minialalipay.ai.application.port;

import java.util.List;

/**
 * SSE 流式回调接口。
 *
 * <p>Controller 层实现此接口将事件转发为 SseEmitter.send()。
 * 方法按调用时序排列：STATUS → TOOL_CALL/TOOL_RESULT/CONTENT（可穿插）
 * → CONFIRMATION/CLARIFICATION（可选）→ DONE。</p>
 */
public interface StreamCallback {

    /** 流程阶段状态，如 INTENT/PARSING/EXECUTING/REPLYING */
    void onStatus(SseEvent.StatusPayload event);

    /** 工具调用开始 */
    void onToolCall(SseEvent.ToolCallPayload event);

    /** 工具调用完成 */
    void onToolResult(SseEvent.ToolResultPayload event);

    /** 自然语言回复片段（逐句推送） */
    void onContentDelta(SseEvent.ContentPayload event);

    /** 高风险操作确认卡片 */
    void onConfirmation(SseEvent.ConfirmationPayload event);

    /** 需要用户澄清 */
    void onClarification(SseEvent.ClarificationPayload event);

    /** 流式结束 */
    void onDone(SseEvent.DonePayload event);

    /** 错误事件。触发后流关闭 */
    void onError(SseEvent.ErrorPayload event);
}
