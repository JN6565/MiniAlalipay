package com.minialalipay.ai.domain.tool;

/**
 * MCP工具风险等级，用于策略网关决定工具是否可调用以及是否需要可信确认上下文。
 */
public enum ToolRiskLevel {
    /** 只读取当前用户有权访问的脱敏数据，不改变业务状态。 */
    READ_ONLY,

    /** 允许创建或校验业务草稿，但不能形成资金事实。 */
    LOW,

    /** 可能触发资金提交，仅在可信UI已经生成有效确认上下文后允许调用。 */
    HIGH;

    /**
     * 判断工具调用是否必须携带可信确认上下文。
     *
     * @return 高风险工具返回 {@code true}
     */
    public boolean requiresConfirmation() {
        return this == HIGH;
    }
}
