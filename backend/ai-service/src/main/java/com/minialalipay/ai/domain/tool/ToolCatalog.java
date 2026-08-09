package com.minialalipay.ai.domain.tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MCP 工具白名单注册表。
 *
 * <p>维护所有已批准的 P0 工具及其风险等级、必填参数和 JSON Schema。
 * 未注册的工具不可被发现或调用。高风险工具的 Schema 不含确认令牌参数。
 * 此领域对象无框架依赖，由 AiServiceCommonConfiguration 注册为 Spring Bean。</p>
 */
public class ToolCatalog {

    private final Map<String, ToolDefinition> tools = Map.ofEntries(
            // ========================
            // READ_ONLY 查询工具（7个）
            // ========================
            Map.entry("search_payees", new ToolDefinition(
                    "search_payees", ToolRiskLevel.READ_ONLY,
                    "搜索收款人。当用户要转账/转钱/汇款时，必须先调用此工具搜索收款人（支持手机号精确搜索或姓名模糊搜索），获取 payeeId 后才能创建转账草稿。", "user-center",
                    "v1", true, 3,
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "query", Map.of("type", "string", "minLength", 1, "maxLength", 64,
                                            "description", "搜索关键词：11位手机号精确匹配，或中文姓名模糊匹配。例如用户说'转给18736330007'则传'18736330007'，说'转给张三'则传'张三'"),
                                    "limit", Map.of("type", "integer", "default", 10, "maximum", 20,
                                            "description", "返回结果数量上限")
                            ),
                            "required", List.of("query"),
                            "additionalProperties", false
                    ),
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "users", Map.of("type", "array",
                                            "items", Map.of("type", "object", "properties", Map.of(
                                                    "userId", Map.of("type", "string"),
                                                    "nickname", Map.of("type", "string"),
                                                    "phoneTail", Map.of("type", "string")
                                            )))
                            )
                    )
            )),
            Map.entry("get_account_summary", new ToolDefinition(
                    "get_account_summary", ToolRiskLevel.READ_ONLY,
                    "查询本人账户摘要", "account-center",
                    "v1", true, 3,
                    Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                    Map.of("type", "object", "properties", Map.of(
                            "accountId", Map.of("type", "string"),
                            "availableFen", Map.of("type", "integer", "format", "int64"),
                            "frozenFen", Map.of("type", "integer", "format", "int64"),
                            "status", Map.of("type", "string")
                    ))
            )),
            Map.entry("get_balance", new ToolDefinition(
                    "get_balance", ToolRiskLevel.READ_ONLY,
                    "查询账户可用余额和冻结金额。当用户询问余额、多少钱、账户资金时使用此工具。注意：此工具只查账户余额，不查花呗/信用额度。", "account-center",
                    "v1", true, 3,
                    Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                    Map.of("type", "object", "properties", Map.of(
                            "availableFen", Map.of("type", "integer", "format", "int64"),
                            "frozenFen", Map.of("type", "integer", "format", "int64")
                    ))
            )),
            Map.entry("list_transactions", new ToolDefinition(
                    "list_transactions", ToolRiskLevel.READ_ONLY,
                    "查询交易明细，支持时间范围、收支方向和状态筛选", "account-center",
                    "v1", true, 3,
                    Map.of("type", "object", "properties", Map.of(
                            "limit", Map.of("type", "integer", "default", 10, "maximum", 50,
                                    "description", "返回条数上限"),
                            "startTime", Map.of("type", "string", "format", "date-time",
                                    "description", "查询起始时间（ISO 8601），如 2026-01-01T00:00:00Z"),
                            "endTime", Map.of("type", "string", "format", "date-time",
                                    "description", "查询截止时间（ISO 8601）"),
                            "direction", Map.of("type", "string", "enum", List.of("IN", "OUT"),
                                    "description", "收支方向筛选：IN=收入，OUT=支出"),
                            "status", Map.of("type", "string", "enum", List.of("SUCCESS", "PROCESSING", "FAILED"),
                                    "description", "交易状态筛选")
                    ), "additionalProperties", false),
                    Map.of("type", "object", "properties", Map.of(
                            "items", Map.of("type", "array"),
                            "nextCursor", Map.of("type", "string")
                    ))
            )),
            Map.entry("get_transaction_status", new ToolDefinition(
                    "get_transaction_status", ToolRiskLevel.READ_ONLY,
                    "查询交易状态", "business-center",
                    "v1", true, 3,
                    Map.of("type", "object", "properties", Map.of(
                            "transactionId", Map.of("type", "string",
                                    "description", "交易ID")
                    ), "required", List.of("transactionId"), "additionalProperties", false),
                    Map.of("type", "object", "properties", Map.of(
                            "transactionId", Map.of("type", "string"),
                            "status", Map.of("type", "string",
                                    "description", "交易状态: SUCCESS/PROCESSING/FAILED")
                    ))
            )),
            Map.entry("get_credit_summary", new ToolDefinition(
                    "get_credit_summary", ToolRiskLevel.READ_ONLY,
                    "查询花呗/信用额度信息，包括总额度、已用额度和可用额度。当用户询问花呗额度、信用额度、花呗可用额度时使用此工具。注意：此工具只查信用额度，不查账户余额。", "account-center",
                    "v1", true, 3,
                    Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                    Map.of("type", "object", "properties", Map.of(
                            "totalLimitFen", Map.of("type", "integer", "format", "int64"),
                            "usedFen", Map.of("type", "integer", "format", "int64"),
                            "availableFen", Map.of("type", "integer", "format", "int64")
                    ))
            )),
            Map.entry("list_credit_bills", new ToolDefinition(
                    "list_credit_bills", ToolRiskLevel.READ_ONLY,
                    "查询本人账单列表", "account-center",
                    "v1", true, 3,
                    Map.of("type", "object", "properties", Map.of(
                            "limit", Map.of("type", "integer", "default", 10)
                    ), "additionalProperties", false),
                    Map.of("type", "object", "properties", Map.of(
                            "bills", Map.of("type", "array")
                    ))
            )),

            // ========================
            // DRAFT 草稿工具（2个）
            // ========================
            Map.entry("create_transfer_draft", new ToolDefinition(
                    "create_transfer_draft", ToolRiskLevel.DRAFT,
                    "创建转账草稿。必须在 search_payees 找到收款人后调用，将 payeeId、金额（分）和备注保存为草稿。", "business-center",
                    "v1", true, 3,
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "payeeId", Map.of("type", "string",
                                            "description", "收款人用户ID，必须来自search_payees返回值"),
                                    "amountFen", Map.of("type", "integer", "format", "int64",
                                            "minimum", 1L, "maximum", 5000000L,
                                            "description", "转账金额（分），1-5000000"),
                                    "remark", Map.of("type", "string",
                                            "minLength", 0, "maxLength", 50,
                                            "description", "转账备注")
                            ),
                            "required", List.of("payeeId", "amountFen"),
                            "additionalProperties", false
                    ),
                    Map.of("type", "object", "properties", Map.of(
                            "draftId", Map.of("type", "string"),
                            "version", Map.of("type", "integer", "format", "int64")
                    ))
            )),
            Map.entry("create_credit_repayment_draft", new ToolDefinition(
                    "create_credit_repayment_draft", ToolRiskLevel.DRAFT,
                    "创建还款金额与分配预览", "account-center",
                    "v1", true, 3,
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "amountFen", Map.of("type", "integer", "format", "int64",
                                            "minimum", 1L, "description", "还款金额（分）")
                            ),
                            "required", List.of("amountFen"),
                            "additionalProperties", false
                    ),
                    Map.of("type", "object", "properties", Map.of(
                            "repaymentDraftId", Map.of("type", "string"),
                            "version", Map.of("type", "integer", "format", "int64")
                    ))
            )),

            // ========================
            // VALIDATION 校验工具（2个）
            // ========================
            Map.entry("validate_transfer_draft", new ToolDefinition(
                    "validate_transfer_draft", ToolRiskLevel.VALIDATION,
                    "执行账户、限额和风控预检", "business-center",
                    "v1", true, 3,
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "draftId", Map.of("type", "string",
                                            "description", "待校验的草稿ID")
                            ),
                            "required", List.of("draftId"),
                            "additionalProperties", false
                    ),
                    Map.of("type", "object", "properties", Map.of(
                            "valid", Map.of("type", "boolean"),
                            "checks", Map.of("type", "object")
                    ))
            )),
            Map.entry("prepare_confirmation_card", new ToolDefinition(
                    "prepare_confirmation_card", ToolRiskLevel.VALIDATION,
                    "生成待用户确认的结构化卡片", "business-center",
                    "v1", true, 3,
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "draftId", Map.of("type", "string")
                            ),
                            "required", List.of("draftId"),
                            "additionalProperties", false
                    ),
                    Map.of("type", "object", "properties", Map.of(
                            "cardType", Map.of("type", "string"),
                            "payeeNickname", Map.of("type", "string"),
                            "payeePhoneTail", Map.of("type", "string"),
                            "amountFen", Map.of("type", "integer", "format", "int64"),
                            "fundingSource", Map.of("type", "string")
                    ))
            )),

            // ========================
            // HIGH_RISK_WRITE 高风险资金工具（2个）
            // Schema 不含确认句柄——由策略网关注入
            // ========================
            Map.entry("submit_confirmed_transfer", new ToolDefinition(
                    "submit_confirmed_transfer", ToolRiskLevel.HIGH_RISK_WRITE,
                    "提交已由可信 UI 确认的转账", "business-center",
                    "v1", true, 3,
                    // 输入仅含 draftId，确认句柄由服务端注入
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "draftId", Map.of("type", "string",
                                            "description", "待提交的草稿ID")
                            ),
                            "required", List.of("draftId"),
                            "additionalProperties", false
                    ),
                    Map.of("type", "object", "properties", Map.of(
                            "transactionId", Map.of("type", "string"),
                            "status", Map.of("type", "string",
                                    "description", "PROCESSING/SUCCESS/FAILED，不得在PROCESSING时表述为成功")
                    ))
            )),
            Map.entry("submit_confirmed_credit_repayment", new ToolDefinition(
                    "submit_confirmed_credit_repayment", ToolRiskLevel.HIGH_RISK_WRITE,
                    "提交已由可信 UI 确认的还款", "account-center",
                    "v1", true, 3,
                    // 输入仅含 repaymentDraftId
                    Map.of("type", "object",
                            "properties", Map.of(
                                    "repaymentDraftId", Map.of("type", "string",
                                            "description", "待提交的还款草稿ID")
                            ),
                            "required", List.of("repaymentDraftId"),
                            "additionalProperties", false
                    ),
                    Map.of("type", "object", "properties", Map.of(
                            "transactionId", Map.of("type", "string"),
                            "status", Map.of("type", "string")
                    ))
            ))
    );

    public Optional<ToolDefinition> lookup(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    public Map<String, ToolDefinition> allTools() {
        return Map.copyOf(tools);
    }

    /**
     * MCP 工具发现接口。
     *
     * <p>返回所有已注册工具的可发现列表，符合 MCP Protocol 的 tools/list 响应格式。
     * Agent 通过此方法动态发现可用工具，无需硬编码工具列表。</p>
     *
     * @return MCP 格式的工具描述列表，每项包含 name、description、inputSchema
     */
    public List<Map<String, Object>> discoverTools() {
        return tools.values().stream()
                .map(tool -> Map.<String, Object>of(
                        "name", tool.toolName(),
                        "description", tool.description(),
                        "inputSchema", tool.inputSchema()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 按风险等级筛选工具发现接口。
     *
     * <p>用于 MCP 层权限控制：不同角色可发现不同风险等级的工具。</p>
     *
     * @param maxRiskLevel 允许的最高风险等级
     * @return 符合条件的工具描述列表
     */
    public List<Map<String, Object>> discoverTools(ToolRiskLevel maxRiskLevel) {
        return tools.values().stream()
                .filter(tool -> tool.riskLevel().ordinal() <= maxRiskLevel.ordinal())
                .map(tool -> Map.<String, Object>of(
                        "name", tool.toolName(),
                        "description", tool.description(),
                        "inputSchema", tool.inputSchema()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 工具定义，含完整的版本化元数据、JSON Schema 和策略信息。
     *
     * @param toolName 工具契约名称
     * @param riskLevel 四级风险等级
     * @param description 中文功能描述
     * @param targetService 目标后端服务
     * @param version Schema 版本号（v1）
     * @param requiresLogin 是否需要用户登录态
     * @param timeoutSeconds 单次调用超时（秒）
     * @param inputSchema 输入 JSON Schema（additionalProperties: false）
     * @param outputSchema 输出 JSON Schema
     */
    public record ToolDefinition(
            String toolName,
            ToolRiskLevel riskLevel,
            String description,
            String targetService,
            String version,
            boolean requiresLogin,
            int timeoutSeconds,
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema
    ) {
        /** 阶段四简化构造：默认 v1、需登录、3 秒超时，Schema 为空 Map。 */
        public ToolDefinition(
                String toolName, ToolRiskLevel riskLevel,
                String description, String targetService) {
            this(toolName, riskLevel, description, targetService, "v1", true, 3,
                    Map.of(), Map.of());
        }
    }
}
