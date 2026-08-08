package com.minialalipay.ai.application.service;

import com.minialalipay.ai.domain.agent.IntentType;

import java.util.List;
import java.util.Map;

/**
 * 用户意图→MCP 工具映射表。
 *
 * @deprecated 已被 Agent 架构替代。AgentLoop 通过 LLM function calling 自主选择工具，
 * 不再使用硬编码的意图→工具映射。保留此类以兼容旧测试代码。
 *
 * <p>定义每种用户意图对应哪些 MCP 工具及其参数的来源，供 Agent 将识别出的
 * 意图编排为工具调用序列。</p>
 */
@Deprecated
public final class IntentToolMapping {

    /** 意图→工具映射表，定义 8 类已知意图对应的工具序列。 */
    private static final Map<IntentType, List<ToolMapping>> INTENT_TO_TOOLS =
            Map.ofEntries(
                    Map.entry(IntentType.BALANCE_QUERY,
                            List.of(new ToolMapping("get_balance", Map.of(), false))),
                    Map.entry(IntentType.TRANSACTION_LIST,
                            List.of(new ToolMapping("list_transactions",
                                    Map.of("limit", ParamSource.CONSTANT,
                                            "startTime", ParamSource.SLOTS_OPTIONAL,
                                            "endTime", ParamSource.SLOTS_OPTIONAL,
                                            "direction", ParamSource.SLOTS_OPTIONAL,
                                            "status", ParamSource.SLOTS_OPTIONAL), false))),
                    Map.entry(IntentType.TRANSACTION_STATUS,
                            List.of(new ToolMapping("get_transaction_status",
                                    Map.of("transactionId", ParamSource.SLOTS), false))),
                    Map.entry(IntentType.USER_SEARCH,
                            List.of(new ToolMapping("search_payees",
                                    Map.of("query", ParamSource.SLOTS,
                                            "limit", ParamSource.CONSTANT), false))),
                    Map.entry(IntentType.CREDIT_SUMMARY,
                            List.of(new ToolMapping("get_credit_summary", Map.of(), false))),
                    Map.entry(IntentType.CREDIT_BILL,
                            List.of(new ToolMapping("list_credit_bills",
                                    Map.of("limit", ParamSource.CONSTANT), false))),
                    Map.entry(IntentType.CREDIT_REPAYMENT,
                            List.of(new ToolMapping("create_credit_repayment_draft",
                                    Map.of("amountFen", ParamSource.SLOTS), false))),
                    Map.entry(IntentType.TRANSFER,
                            List.of(
                                    // 第一步：通过手机号搜索收款人，获取 payeeId
                                    new ToolMapping("search_payees",
                                            Map.of("query", ParamSource.SLOTS,
                                                    "limit", ParamSource.CONSTANT),
                                            false),
                                    // 第二步：创建转账草稿（需要前一步返回的 payeeId）
                                    new ToolMapping("create_transfer_draft",
                                            Map.of("payeeId", ParamSource.PREVIOUS_RESULT,
                                                    "amountFen", ParamSource.SLOTS,
                                                    "remark", ParamSource.SLOTS_OPTIONAL),
                                            true),
                                    // 第三步：校验草稿
                                    new ToolMapping("validate_transfer_draft",
                                            Map.of("draftId", ParamSource.PREVIOUS_RESULT),
                                            true),
                                    // 第四步：生成确认卡片
                                    new ToolMapping("prepare_confirmation_card",
                                            Map.of("draftId", ParamSource.PREVIOUS_RESULT),
                                            true),
                                    // 第五步：提交已确认转账
                                    new ToolMapping("submit_confirmed_transfer",
                                            Map.of("draftId", ParamSource.PREVIOUS_RESULT),
                                            true))));

    private IntentToolMapping() {
        // 静态工具类，禁止实例化
    }

    /**
     * 获取指定意图对应的工具映射列表。
     *
     * @param intent 用户意图
     * @return 工具映射列表；当意图为 {@link IntentType#UNKNOWN} 或未知时返回空列表
     */
    public static List<ToolMapping> getToolsForIntent(IntentType intent) {
        return INTENT_TO_TOOLS.getOrDefault(intent, List.of());
    }

    /**
     * 工具映射：描述单个工具的调用方式。
     *
     * @param toolName         工具名，与 ToolCatalog 一致
     * @param paramSources     参数名→来源，描述每个参数如何获取
     * @param isChainDependent 是否链式依赖前驱工具，前驱失败时跳过
     */
    public record ToolMapping(
            String toolName,
            Map<String, ParamSource> paramSources,
            boolean isChainDependent
    ) {
    }

    /**
     * 参数来源枚举。
     */
    public enum ParamSource {
        /** 从 LLM 提取的会话槽位中获取。 */
        SLOTS,
        /** 从槽位获取，缺失时用默认值。 */
        SLOTS_OPTIONAL,
        /** 从前一个工具返回的 data 中获取。 */
        PREVIOUS_RESULT,
        /** 固定默认值（如 limit=10），不从槽位取。 */
        CONSTANT
    }
}
