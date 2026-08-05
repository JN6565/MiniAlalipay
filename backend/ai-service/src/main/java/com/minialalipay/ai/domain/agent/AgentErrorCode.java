package com.minialalipay.ai.domain.agent;

import com.minialalipay.common.error.ErrorCode;

/**
 * AI 服务领域错误码枚举。
 *
 * <p>{@code code}、{@code message} 和 {@code httpStatus} 必须与
 * {@code contracts/error-codes/error-codes.yaml} 完全一致。
 * 禁止把用户、账户、交易、信用或 AI 领域错误码集中放入 platform-common。</p>
 *
 * <h3>各错误码说明</h3>
 * <ul>
 *   <li>{@link #AGENT_BUSY}：同一会话正在处理上一条消息，拒绝并发请求</li>
 *   <li>{@link #SESSION_NOT_FOUND}：会话不存在或已过期关闭</li>
 *   <li>{@link #AGENT_SESSION_EXPIRED}：会话超时未活跃</li>
 *   <li>{@link #INTENT_LOW_CONFIDENCE}：意图置信度不足，需要澄清</li>
 *   <li>{@link #TOOL_FORBIDDEN}：当前主体无权调用该工具</li>
 *   <li>{@link #TOOL_UNAVAILABLE}：MCP 工具调用超时或下游不可用</li>
 *   <li>{@link #PROMPT_INJECTION_REJECTED}：用户输入触发注入检测规则</li>
 *   <li>{@link #IDEMPOTENCY_CONFLICT}：同键异参冲突</li>
 *   <li>{@link #VERSION_CONFLICT}：CAS 版本冲突</li>
 *   <li>{@link #LLM_UNAVAILABLE}：LLM 服务超时、熔断或不可用</li>
 * </ul>
 */
public enum AgentErrorCode implements ErrorCode {
    /** 当前会话正在处理上一条消息，请稍后重试。 */
    AGENT_BUSY("AGENT_BUSY", "当前会话正在处理上一条消息", 409),

    /** AI 会话不存在（未找到、已关闭或已过期）。 */
    SESSION_NOT_FOUND("SESSION_NOT_FOUND", "AI 会话不存在", 404),

    /** 会话已过期，请重新开始对话。 */
    AGENT_SESSION_EXPIRED("AGENT_SESSION_EXPIRED", "会话已过期，请重新开始对话", 404),

    /** 未能识别用户意图，需要进一步澄清。 */
    INTENT_LOW_CONFIDENCE("INTENT_LOW_CONFIDENCE", "未能识别您的意图，请重新描述", 422),

    /** 当前主体无权调用该工具。 */
    TOOL_FORBIDDEN("TOOL_FORBIDDEN", "当前不支持该操作", 403),

    /** MCP 工具服务暂不可用（超时、熔断或下游故障）。 */
    TOOL_UNAVAILABLE("TOOL_UNAVAILABLE", "工具服务暂不可用", 503),

    /** 请求内容违反 AI 安全策略（提示注入、越权指令等）。 */
    PROMPT_INJECTION_REJECTED("PROMPT_INJECTION_REJECTED", "请求包含不安全内容，已被拒绝", 400),

    /** 同一幂等键的请求摘要不一致。 */
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "请求内容与前次不一致，请重新发起", 409),

    /** 数据版本冲突，请刷新后重试。 */
    VERSION_CONFLICT("VERSION_CONFLICT", "数据已被他人修改，请刷新后重试", 409),

    /** LLM 服务暂时不可用。 */
    LLM_UNAVAILABLE("LLM_UNAVAILABLE", "AI 服务暂时不可用，请稍后重试", 503);

    private final String code;
    private final String message;
    private final int httpStatus;

    AgentErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
