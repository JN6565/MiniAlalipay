package com.minialalipay.ai.domain.tool;

/**
 * MCP 工具风险等级，用于策略网关决定工具是否可调用以及是否需要可信确认上下文。
 *
 * <p>风险等级从低到高：只读查询 → 草稿创建 → 校验预检 → 高风险资金提交。
 * 只有 {@link #HIGH_RISK_WRITE} 级别的工具必须携带由可信 UI 生成的有效确认上下文，
 * 且确认上下文不得由模型参数传入。</p>
 *
 * <h3>各等级说明</h3>
 * <ul>
 *   <li>{@link #READ_ONLY}：只读取当前用户有权访问的脱敏数据，不改变任何业务状态</li>
 *   <li>{@link #DRAFT}：允许创建或修改业务草稿，但不能形成资金事实</li>
 *   <li>{@link #VALIDATION}：执行账户、限额或风控预检，不得产生资金副作用</li>
 *   <li>{@link #HIGH_RISK_WRITE}：提交已由可信 UI 确认的资金操作，默认不可直接执行</li>
 * </ul>
 */
public enum ToolRiskLevel {
    /** 只读取当前用户有权访问的脱敏数据，不改变业务状态。 */
    READ_ONLY,

    /** 允许创建或校验业务草稿，但不能形成资金事实。 */
    DRAFT,

    /** 执行账户、限额和风控预检，不得产生资金副作用。 */
    VALIDATION,

    /** 可能触发资金提交，仅在可信 UI 已经生成有效确认上下文后允许调用。 */
    HIGH_RISK_WRITE;

    /**
     * 判断工具调用是否必须携带可信确认上下文。
     *
     * @return 仅高风险写入工具返回 {@code true}
     */
    public boolean requiresConfirmation() {
        return this == HIGH_RISK_WRITE;
    }

    /**
     * 判断当前风险等级是否允许产生业务副作用。
     *
     * @return 只读工具返回 {@code false}，其余返回 {@code true}
     */
    public boolean allowsSideEffects() {
        return this != READ_ONLY;
    }
}
