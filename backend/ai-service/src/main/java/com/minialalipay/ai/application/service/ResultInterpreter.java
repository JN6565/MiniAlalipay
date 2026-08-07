package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 工具结果解释引擎。
 *
 * <p>将工具返回的稳定状态码和标准原因转换为中文自然语言解释。
 * 核心安全规则：不得将 {@code PROCESSING}、{@code COMPENSATING}、
 * {@code MANUAL_REVIEW} 解释为“成功”或承诺伪造的到账时间。</p>
 *
 * <h3>解释规则</h3>
 * <ul>
 *   <li>成功 → 简洁确认</li>
 *   <li>处理中 → 说明当前正在处理，引导稍后查询</li>
 *   <li>失败 → 说明失败原因，引导用户操作（如调低金额、检查余额）</li>
 *   <li>余额不足 → 引导查看余额或调低金额，不虚构充值能力</li>
 *   <li>超时 → 建议查询原状态，不承诺结果</li>
 * </ul>
 */
@Service
public class ResultInterpreter {

    private static final Logger log = LoggerFactory.getLogger(ResultInterpreter.class);

    // ---- 不可被解释为成功的状态 ----
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPENSATING = "COMPENSATING";
    private static final String STATUS_MANUAL_REVIEW = "MANUAL_REVIEW";

    // ---- 结果码 ----
    private static final String CODE_SUCCESS = "SUCCESS";
    private static final String CODE_TOOL_UNAVAILABLE = "TOOL_UNAVAILABLE";

    /** 预置中文降级话术，与标准错误码对应 */
    static final String FALLBACK_TOOL_TIMEOUT = "操作处理中，请稍后查询状态确认结果。";
    static final String FALLBACK_TOOL_UNAVAILABLE = "服务暂不可用，请稍后重试或使用传统操作表单。";
    static final String FALLBACK_UNKNOWN_RESULT = "操作已提交，请刷新页面或稍后查询最新状态。";

    /**
     * 根据工具名和结果生成中文解释。
     *
     * @param toolName 工具名
     * @param result 工具调用结果
     * @return 自然语言解释（中文）
     */
    public String interpret(String toolName, ToolResult result) {
        if (result == null) {
            return FALLBACK_TOOL_UNAVAILABLE;
        }

        // 工具不可用
        if (CODE_TOOL_UNAVAILABLE.equals(result.resultCode())) {
            return fallbackForTool(toolName);
        }

        // 成功结果
        if (CODE_SUCCESS.equals(result.resultCode()) && result.data() != null) {
            return interpretSuccess(toolName, result.data());
        }

        // 未知结果
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            return result.errorMessage();
        }
        return FALLBACK_UNKNOWN_RESULT;
    }

    /**
     * 解析工具返回数据中的状态，生成准确解释。
     * {@code PROCESSING/COMPENSATING/MANUAL_REVIEW} 不得表述为成功。
     */
    private String interpretSuccess(String toolName, Map<String, Object> data) {
        String status = (String) data.get("status");
        if (status != null) {
            // 不可被解释为成功的终态
            if (STATUS_PROCESSING.equals(status)) {
                return switch (toolName) {
                    case "submit_confirmed_transfer" ->
                            "转账已提交处理，请稍后查询转账状态。";
                    case "submit_confirmed_credit_repayment" ->
                            "还款已提交处理，请稍后查询账单状态。";
                    default ->
                            "您的操作已提交，正在处理中，请稍后查询结果。";
                };
            }
            if (STATUS_COMPENSATING.equals(status)) {
                return "系统正在处理异常恢复，请稍后查询状态。结果以最终查询为准。";
            }
            if (STATUS_MANUAL_REVIEW.equals(status)) {
                return "您的操作已提交人工审核，审核完成后将通知您。";
            }

            // 确定终态
            return switch (status) {
                case "SUCCESS" -> interpretFinalSuccess(toolName, data);
                case "FAILED" -> interpretFailure(data);
                default -> "操作已完成，请查看详情。";
            };
        }

        // 无 status 字段时按工具成功处理
        return interpretFinalSuccess(toolName, data);
    }

    /**
     * 生成成功场景的中文解释。不承诺虚构的到账时间。
     */
    private String interpretFinalSuccess(String toolName, Map<String, Object> data) {
        return switch (toolName) {
            case "search_payees" -> {
                Object users = data.get("users");
                int count = (users instanceof java.util.List<?> list) ? list.size() : 0;
                yield count > 0
                        ? "为您找到 " + count + " 位候选收款人。"
                        : "未找到匹配的收款人，请尝试其他搜索条件。";
            }
            case "get_balance" -> {
                Object fen = data.get("availableFen");
                long amount = (fen instanceof Number n) ? n.longValue() : 0L;
                yield "您当前账户可用余额为 " + formatFen(amount) + " 元。";
            }
            case "get_account_summary" -> {
                Object fen = data.get("availableFen");
                long amount = (fen instanceof Number n) ? n.longValue() : 0L;
                yield "您的账户状态正常，可用余额 " + formatFen(amount) + " 元。";
            }
            case "get_credit_summary" -> {
                Object used = data.get("usedFen");
                Object total = data.get("totalLimitFen");
                long usedFen = (used instanceof Number n) ? n.longValue() : 0L;
                long totalFen = (total instanceof Number n) ? n.longValue() : 500_000L;
                yield "您的 Mini 花呗总额度 " + formatFen(totalFen)
                        + " 元，已用 " + formatFen(usedFen) + " 元。";
            }
            case "submit_confirmed_transfer" ->
                    "转账成功！资金已从您的账户扣除。";
            case "submit_confirmed_credit_repayment" ->
                    "还款成功！花呗账单已更新。";
            case "create_transfer_draft" ->
                    "转账草稿已保存，请在确认卡片中核对信息后完成支付。";
            case "create_credit_repayment_draft" ->
                    "还款草稿已保存，请确认还款金额。";
            case "validate_transfer_draft" ->
                    "转账信息校验通过，可以提交。";
            case "prepare_confirmation_card" ->
                    "请核对以下信息后完成支付：\n"
                            + "收款人: " + data.getOrDefault("payeeNickname", "")
                            + "\n金额: " + formatFen(data.get("amountFen")) + " 元";
            case "list_transactions" -> {
                // GAP-1：根据筛选参数生成更精确的结果解释
                Object items = data.get("items");
                int count = (items instanceof java.util.List<?> list) ? list.size() : 0;
                String direction = (String) data.get("direction");
                String filterDesc = direction != null
                        ? ("IN".equals(direction) ? "收入" : "支出") : "";
                yield count > 0
                        ? "为您找到 " + count + " 条" + filterDesc + "交易明细。"
                        : (filterDesc.isEmpty() ? "暂无交易记录。" : "暂无" + filterDesc + "交易记录。");
            }
            case "list_credit_bills" ->
                    "以下是您的花呗账单。";
            case "get_transaction_status" -> {
                Object s = data.get("status");
                yield s != null ? "该笔交易当前状态: " + s : "未能获取交易状态。";
            }
            default ->
                    "操作成功。";
        };
    }

    private String interpretFailure(Map<String, Object> data) {
        Object reason = data.get("reason");
        if (reason != null) {
            String reasonStr = reason.toString();
            if (reasonStr.contains("INSUFFICIENT_BALANCE") || reasonStr.contains("余额不足")) {
                return "余额不足，无法完成支付。请查看余额后调低转账金额。";
            }
            if (reasonStr.contains("LIMIT_EXCEEDED") || reasonStr.contains("超限")) {
                return "超出当日限额，请调低金额或明天再试。";
            }
            if (reasonStr.contains("VERSION_CONFLICT")) {
                return "数据已被更新，请刷新后重试。";
            }
        }
        return "操作未成功，请稍后重试。";
    }

    /**
     * 工具不可用时的降级话术，不虚构状态。
     */
    private String fallbackForTool(String toolName) {
        return switch (toolName) {
            case "get_balance", "get_account_summary" ->
                    "暂时无法查询余额，请稍后重试或刷新页面。";
            case "submit_confirmed_transfer" ->
                    FALLBACK_TOOL_TIMEOUT;
            case "submit_confirmed_credit_repayment" ->
                    FALLBACK_TOOL_TIMEOUT;
            default -> FALLBACK_TOOL_UNAVAILABLE;
        };
    }

    // ---- 工具方法 ----

    /**
     * 将分金额格式化为可读的元字符串。仅供展示使用，业务计算始终使用分。
     */
    static String formatFen(Object fenValue) {
        long fen = (fenValue instanceof Number n) ? n.longValue() : 0L;
        return String.format("%,.2f", fen / 100.0);
    }
}
