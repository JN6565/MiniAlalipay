package com.minialalipay.ai.domain.tool;

import java.util.List;
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
            // ========================
            // READ_ONLY 查询工具（7个）
            // ========================
            Map.entry("search_payees", new ToolDefinition(
                    "search_payees", ToolRiskLevel.READ_ONLY,
                    "查询候选收款人", "user-center",
                    "v1", true, 3,
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "query", Map.of("type", "string", "minLength", 1, "maxLength", 64,
                                            "description", "搜索手机号（11 位精确匹配）"),
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
                    "查询实时余额", "account-center",
                    "v1", true, 3,
                    Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                    Map.of("type", "object", "properties", Map.of(
                            "availableFen", Map.of("type", "integer", "format", "int64"),
                            "frozenFen", Map.of("type", "integer", "format", "int64")
                    ))
            )),
            Map.entry("list_transactions", new ToolDefinition(
                    "list_transactions", ToolRiskLevel.READ_ONLY,
                    "查询交易明细", "account-center",
                    "v1", true, 3,
                    Map.of("type", "object", "properties", Map.of(
                            "limit", Map.of("type", "integer", "default", 10, "maximum", 50)
                    ), "additionalProperties", false),
                    Map.of("type", "object", "properties", Map.of(
                            "transactions", Map.of("type", "array")
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
                    "查询本人额度与未出账", "account-center",
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
                    "保存结构化转账草稿", "business-center",
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
