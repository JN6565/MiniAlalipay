package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.AccountCenterPort;
import com.minialalipay.ai.application.port.BusinessCenterPort;
import com.minialalipay.ai.application.port.ToolResult;
import com.minialalipay.ai.application.port.UserCenterPort;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.ai.domain.tool.ToolRiskLevel;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * MCP 工具路由器（阶段五：端口驱动）。
 *
 * <p>根据工具名将调用路由到对应的三中心端口实现。
 * 只读工具超时后自动重试一次；写工具超时后查询原资源状态，不盲重试。</p>
 */
@Service
public class ToolRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolRouter.class);
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_TOOL_UNAVAILABLE = "TOOL_UNAVAILABLE";

    private final UserCenterPort userCenterPort;
    private final AccountCenterPort accountCenterPort;
    private final BusinessCenterPort businessCenterPort;
    private final ToolCatalog toolCatalog;
    private final int toolTimeoutMs;

    public ToolRouter(
            UserCenterPort userCenterPort,
            AccountCenterPort accountCenterPort,
            BusinessCenterPort businessCenterPort,
            ToolCatalog toolCatalog,
            @Value("${ai.mcp.tool-timeout:3s}") String toolTimeout
    ) {
        this.userCenterPort = userCenterPort;
        this.accountCenterPort = accountCenterPort;
        this.businessCenterPort = businessCenterPort;
        this.toolCatalog = toolCatalog;
        this.toolTimeoutMs = (int) parseDurationMillis(toolTimeout);
    }

    /**
     * 根据工具名路由到对应端口实现。
     *
     * @param toolName 工具契约名称
     * @param params 工具输入参数（由 LLM 生成，不可信）
     * @param userId 当前用户 ID（由服务端派生）
     * @return 工具调用结果
     */
    public ToolResult route(String toolName, Map<String, Object> params, String userId) {
        ToolCatalog.ToolDefinition def = toolCatalog.lookup(toolName)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE));

        try {
            ToolResult result = executeWithTimeout(toolName, params, userId, def);
            return result;
        } catch (TimeoutException e) {
            log.warn("工具调用超时: tool={}, timeout={}ms", toolName, toolTimeoutMs);
            if (def.riskLevel() == ToolRiskLevel.READ_ONLY) {
                log.info("只读工具重试一次: tool={}", toolName);
                try {
                    return executeWithTimeout(toolName, params, userId, def);
                } catch (TimeoutException e2) {
                    return new ToolResult(RESULT_TOOL_UNAVAILABLE, Map.of(),
                            "工具调用超时，请稍后重试", toolTimeoutMs);
                }
            }
            return new ToolResult(RESULT_TOOL_UNAVAILABLE, Map.of(),
                    "工具调用超时，请稍后重试", toolTimeoutMs);
        }
    }

    private ToolResult executeWithTimeout(
            String toolName, Map<String, Object> params,
            String userId, ToolCatalog.ToolDefinition def
    ) throws TimeoutException {
        long start = System.currentTimeMillis();
        // 在提交异步任务前捕获当前线程的 Bearer Token，
        // 因为 CompletableFuture.supplyAsync 在另一线程执行，ThreadLocal 无法自动传递
        final String bearerToken = RequestContext.getBearerToken();
        try {
            CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(() -> {
                RequestContext.setBearerToken(bearerToken);
                return dispatch(toolName, params, userId);
            });
            return future.get(toolTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw e;
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            Throwable root = e;
            // 展开 CompletableFuture 包装的 ExecutionException
            while ((root instanceof java.util.concurrent.ExecutionException
                    || root instanceof java.util.concurrent.CompletionException)
                    && root.getCause() != null) {
                root = root.getCause();
            }
            log.warn("工具调用失败: tool={}, exception={}, message={}",
                    toolName, root.getClass().getSimpleName(), root.getMessage());
            String errorMsg = root.getMessage();
            if (errorMsg == null || errorMsg.isBlank()) {
                errorMsg = root.getClass().getSimpleName() + "（无详细描述）";
            }
            return new ToolResult(RESULT_TOOL_UNAVAILABLE, Map.of(),
                    "工具调用失败: " + errorMsg, duration);
        }
    }

    /**
     * 按工具名分配到对应端口方法并转换结果为 ToolResult。
     *
     * <p>工具返回内容在进入 Prompt 前需经过结果解释引擎脱敏和状态校验。</p>
     */
    private ToolResult dispatch(String toolName, Map<String, Object> params, String userId) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = switch (toolName) {
            // ---- 用户中心 ----
            case "search_payees" -> {
                String query = (String) params.getOrDefault("query", "");
                int limit = ((Number) params.getOrDefault("limit", 10)).intValue();
                List<Map<String, Object>> users = userCenterPort.searchPayees(userId, query, limit);
                // 同时返回 users 列表和第一个用户的 payeeId，供后续工具链使用
                java.util.Map<String, Object> result = new java.util.HashMap<>();
                result.put("users", users);
                if (users != null && !users.isEmpty()) {
                    Map<String, Object> firstUser = users.get(0);
                    Object userIdValue = firstUser.get("userId");
                    if (userIdValue != null) {
                        result.put("payeeId", userIdValue.toString());
                    }
                }
                yield result;
            }

            // ---- 账户中心 ----
            case "get_account_summary" -> accountCenterPort.getAccountSummary(userId);
            case "get_balance" -> accountCenterPort.getBalance(userId);
            case "list_transactions" -> {
                int limit = ((Number) params.getOrDefault("limit", 10)).intValue();
                String startTime = (String) params.get("startTime");
                String endTime = (String) params.get("endTime");
                String direction = (String) params.get("direction");
                String status = (String) params.get("status");
                Map<String, Object> result = accountCenterPort.listTransactions(
                        userId, limit, startTime, endTime, direction, status);
                // 将筛选参数注入返回数据，供 ResultInterpreter 生成精确文案
                if (direction != null && !direction.isBlank()) {
                    java.util.Map<String, Object> enriched = new java.util.HashMap<>(result);
                    enriched.put("direction", direction);
                    yield enriched;
                }
                yield result;
            }
            case "get_credit_summary" -> accountCenterPort.getCreditSummary(userId);
            case "list_credit_bills" -> {
                int limit = ((Number) params.getOrDefault("limit", 10)).intValue();
                yield accountCenterPort.listCreditBills(userId, limit);
            }

            // ---- 业务中心 ----
            case "get_transaction_status" -> {
                String txnId = (String) params.getOrDefault("transactionId", "");
                yield businessCenterPort.getTransferStatus(userId, txnId);
            }
            case "create_transfer_draft" -> {
                String payeeId = (String) params.get("payeeId");
                long amountFen = ((Number) params.getOrDefault("amountFen", 0L)).longValue();
                String remark = (String) params.getOrDefault("remark", "");
                String idempotencyKey = (String) params.getOrDefault("idempotencyKey",
                        userId + "-draft-" + System.currentTimeMillis());
                yield businessCenterPort.createTransferDraft(
                        userId, payeeId, amountFen, remark, idempotencyKey);
            }
            case "validate_transfer_draft" -> {
                String draftId = (String) params.get("draftId");
                // 版本号由 create_transfer_draft 返回，经 cumulativeParams 传递
                long version = ((Number) params.getOrDefault("version", 0L)).longValue();
                String idempotencyKey = (String) params.getOrDefault("idempotencyKey",
                        userId + "-validate-" + System.currentTimeMillis());
                yield businessCenterPort.validateTransferDraft(userId, draftId, version, idempotencyKey);
            }
            case "prepare_confirmation_card" -> {
                String draftId = (String) params.get("draftId");
                yield businessCenterPort.prepareConfirmationCard(userId, draftId);
            }
            case "submit_confirmed_transfer" -> {
                String draftId = (String) params.get("draftId");
                String confirmationHandle = (String) params.get("confirmationHandle");
                String idempotencyKey = (String) params.getOrDefault("idempotencyKey",
                        userId + "-submit-" + System.currentTimeMillis());
                yield businessCenterPort.submitConfirmedTransfer(
                        userId, draftId, confirmationHandle, idempotencyKey);
            }

            // ---- 账户中心（信用还款） ----
            case "create_credit_repayment_draft" -> {
                long amountFen = ((Number) params.getOrDefault("amountFen", 0L)).longValue();
                String idempotencyKey = (String) params.getOrDefault("idempotencyKey",
                        userId + "-repay-draft-" + System.currentTimeMillis());
                yield accountCenterPort.createCreditRepaymentDraft(
                        userId, amountFen, idempotencyKey);
            }
            case "submit_confirmed_credit_repayment" -> {
                String repaymentDraftId = (String) params.get("repaymentDraftId");
                String paymentProofToken = (String) params.get("paymentProofToken");
                String idempotencyKey = (String) params.getOrDefault("idempotencyKey",
                        userId + "-repay-submit-" + System.currentTimeMillis());
                yield accountCenterPort.submitCreditRepayment(
                        userId, repaymentDraftId, paymentProofToken, idempotencyKey);
            }

            default -> throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
        };
        int duration = (int) (System.currentTimeMillis() - start);
        return new ToolResult(RESULT_SUCCESS, data, null, duration);
    }

    private static long parseDurationMillis(String duration) {
        String trimmed = duration.trim().toLowerCase();
        if (trimmed.endsWith("ms")) return Long.parseLong(trimmed.replace("ms", ""));
        if (trimmed.endsWith("s")) return Long.parseLong(trimmed.replace("s", "")) * 1000;
        if (trimmed.endsWith("m")) return Long.parseLong(trimmed.replace("m", "")) * 60000;
        return Long.parseLong(trimmed);
    }
}
