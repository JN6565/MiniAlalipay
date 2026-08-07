package com.minialalipay.ai.application.port;

import java.util.List;
import java.util.Map;

/**
 * SSE 流式回调接口。
 *
 * <p>在 Agent 消息处理的关键节点发射事件，由 SSE 端点实现将事件推送给客户端。
 * 所有方法均为异步调用，不阻塞主流程。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>不得通过回调传输支付密码、确认令牌、完整账号</li>
 *   <li>金额使用 long 分，不传输浮点金额</li>
 * </ul>
 */
public interface StreamCallback {

    /**
     * 处理阶段状态更新。
     *
     * @param stage 阶段标识（如 INTENT、TOOL_CALL、DONE）
     * @param message 人类可读的状态描述
     */
    void onStatus(String stage, String message);

    /**
     * 工具调用开始。
     *
     * @param toolName 工具名称
     * @param status 调用状态（如 running）
     */
    void onToolCall(String toolName, String status);

    /**
     * 工具调用完成，附带结构化数据供前端渲染卡片。
     *
     * @param toolName 工具名称
     * @param status 结果状态（如 success、failed）
     * @param summary 结果摘要（人类可读）
     * @param data 工具返回的结构化数据（如余额、额度、交易列表等），可为空
     */
    void onToolResult(String toolName, String status, String summary, Map<String, Object> data);

    /**
     * 内容增量推送。
     *
     * @param delta 文本片段（通常为 3-5 个字符）
     */
    void onContentDelta(String delta);

    /**
     * 澄清引导。
     *
     * @param question 澄清问题
     * @param options 可选快捷回复（可为空）
     */
    void onClarification(String question, List<ClarificationOption> options);

    /**
     * 流式完成。
     *
     * @param messageId 助手消息 ID
     * @param sessionId 会话 ID
     * @param intent 识别的意图
     */
    void onDone(String messageId, String sessionId, String intent);

    /**
     * 错误发生。
     *
     * @param code 错误码
     * @param message 错误描述
     */
    void onError(String code, String message);

    /**
     * 澄清选项。
     */
    record ClarificationOption(String id, String label) {}
}
