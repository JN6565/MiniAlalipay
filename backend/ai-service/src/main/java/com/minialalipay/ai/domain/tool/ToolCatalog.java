package com.minialalipay.ai.domain.tool;

import java.util.Map;
import java.util.Optional;

/**
 * MCP 工具白名单注册表。
 *
 * <p>维护所有已批准的 P0 工具及其风险等级、必填参数和 JSON Schema。
 * 未注册的工具不可被发现或调用。高风险工具的 Schema 不含确认令牌参数。
 * 此领域对象无框架依赖，由 AiServiceCommonConfiguration 注册为 Spring Bean。</p>
 */
public class ToolCatalog {

    private final Map<String, ToolDefinition> tools = Map.ofEntries(
            // ---- READ_ONLY 查询工具 ----
            Map.entry("search_payees", new ToolDefinition(
                    "search_payees", ToolRiskLevel.READ_ONLY,
                    "查询候选收款人", "user-center")),
            Map.entry("get_account_summary", new ToolDefinition(
                    "get_account_summary", ToolRiskLevel.READ_ONLY,
                    "查询本人账户摘要", "account-center")),
            Map.entry("get_balance", new ToolDefinition(
                    "get_balance", ToolRiskLevel.READ_ONLY,
                    "查询实时余额", "account-center")),
            Map.entry("list_transactions", new ToolDefinition(
                    "list_transactions", ToolRiskLevel.READ_ONLY,
                    "查询交易明细", "account-center")),
            Map.entry("get_transaction_status", new ToolDefinition(
                    "get_transaction_status", ToolRiskLevel.READ_ONLY,
                    "查询交易状态", "business-center")),
            Map.entry("get_credit_summary", new ToolDefinition(
                    "get_credit_summary", ToolRiskLevel.READ_ONLY,
                    "查询本人额度与未出账", "account-center")),
            Map.entry("list_credit_bills", new ToolDefinition(
                    "list_credit_bills", ToolRiskLevel.READ_ONLY,
                    "查询本人账单列表", "account-center")),

            // ---- DRAFT 草稿工具 ----
            Map.entry("create_transfer_draft", new ToolDefinition(
                    "create_transfer_draft", ToolRiskLevel.DRAFT,
                    "保存结构化转账草稿", "business-center")),
            Map.entry("create_credit_repayment_draft", new ToolDefinition(
                    "create_credit_repayment_draft", ToolRiskLevel.DRAFT,
                    "创建还款金额与分配预览", "account-center")),

            // ---- VALIDATION 校验工具 ----
            Map.entry("validate_transfer_draft", new ToolDefinition(
                    "validate_transfer_draft", ToolRiskLevel.VALIDATION,
                    "执行账户、限额和风控预检", "business-center")),
            Map.entry("prepare_confirmation_card", new ToolDefinition(
                    "prepare_confirmation_card", ToolRiskLevel.VALIDATION,
                    "生成待用户确认的结构化卡片", "business-center")),

            // ---- HIGH_RISK_WRITE 高风险资金工具 ----
            Map.entry("submit_confirmed_transfer", new ToolDefinition(
                    "submit_confirmed_transfer", ToolRiskLevel.HIGH_RISK_WRITE,
                    "提交已由可信 UI 确认的转账", "business-center")),
            Map.entry("submit_confirmed_credit_repayment", new ToolDefinition(
                    "submit_confirmed_credit_repayment", ToolRiskLevel.HIGH_RISK_WRITE,
                    "提交已由可信 UI 确认的还款", "account-center"))
    );

    /**
     * 按工具名查找定义。
     *
     * @param toolName 工具契约名称
     * @return 工具定义，未注册时返回 {@link Optional#empty()}
     */
    public Optional<ToolDefinition> lookup(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    /** @return 全部已注册工具的定义（不可变） */
    public Map<String, ToolDefinition> allTools() {
        return Map.copyOf(tools);
    }

    /**
     * 工具定义，包含元数据和风险等级。
     *
     * @param toolName 工具契约名称
     * @param riskLevel 四级风险等级
     * @param description 中文功能描述
     * @param targetService 目标后端服务（user-center / account-center / business-center）
     * @param version Schema 版本号（当前 v1）
     * @param requiresLogin 是否需要用户登录态
     * @param timeoutSeconds 单次调用超时（秒），默认 3
     */
    public record ToolDefinition(
            String toolName,
            ToolRiskLevel riskLevel,
            String description,
            String targetService,
            String version,
            boolean requiresLogin,
            int timeoutSeconds
    ) {
        /** 阶段四简化构造：默认 v1、需登录、3 秒超时。 */
        public ToolDefinition(
                String toolName, ToolRiskLevel riskLevel,
                String description, String targetService) {
            this(toolName, riskLevel, description, targetService, "v1", true, 3);
        }
    }
}
