package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.ToolResult;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.domain.tool.ToolRiskLevel;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.ai.infrastructure.client.mock.MockAccountCenterClient;
import com.minialalipay.ai.infrastructure.client.mock.MockBusinessCenterClient;
import com.minialalipay.ai.infrastructure.client.mock.MockUserCenterClient;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * MCP 工具路由器。
 *
 * <p>根据工具名将调用路由到对应的三中心 Mock 客户端。
 * 只读工具超时后自动重试一次；写工具超时后查询原资源状态，不盲重试。</p>
 */
@Service
public class ToolRouter {

    private static final Logger log = LoggerFactory.getLogger(ToolRouter.class);
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_TOOL_UNAVAILABLE = "TOOL_UNAVAILABLE";

    private final MockUserCenterClient userCenterClient;
    private final MockAccountCenterClient accountCenterClient;
    private final MockBusinessCenterClient businessCenterClient;
    private final ToolCatalog toolCatalog;
    private final int toolTimeoutMs;

    public ToolRouter(
            MockUserCenterClient userCenterClient,
            MockAccountCenterClient accountCenterClient,
            MockBusinessCenterClient businessCenterClient,
            ToolCatalog toolCatalog,
            @Value("${ai.mcp.tool-timeout:3s}") String toolTimeout
    ) {
        this.userCenterClient = userCenterClient;
        this.accountCenterClient = accountCenterClient;
        this.businessCenterClient = businessCenterClient;
        this.toolCatalog = toolCatalog;
        this.toolTimeoutMs = (int) parseDurationMillis(toolTimeout);
    }

    /**
     * 根据工具名路由到对应的下游客户端。
     *
     * @param toolName 工具契约名称
     * @param params 工具输入参数
     * @param userId 当前用户 ID
     * @param retryIfReadOnly 是否对只读工具重试一次
     * @return 工具调用结果
     */
    public ToolResult route(String toolName, Map<String, Object> params,
                            String userId, boolean retryIfReadOnly) {
        ToolCatalog.ToolDefinition def = toolCatalog.lookup(toolName)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE));

        try {
            return executeWithTimeout(toolName, params, userId, def);
        } catch (TimeoutException e) {
            log.warn("工具调用超时: tool={}, timeout={}ms", toolName, toolTimeoutMs);
            if (retryIfReadOnly && def.riskLevel() == ToolRiskLevel.READ_ONLY) {
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
        try {
            CompletableFuture<ToolResult> future = CompletableFuture.supplyAsync(() ->
                    dispatch(toolName, params, userId, def.targetService()));
            return future.get(toolTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw e;
        } catch (Exception e) {
            int duration = (int) (System.currentTimeMillis() - start);
            return new ToolResult(RESULT_TOOL_UNAVAILABLE, Map.of(),
                    "工具调用失败: " + e.getMessage(), duration);
        }
    }

    private ToolResult dispatch(String toolName, Map<String, Object> params,
                                String userId, String targetService) {
        long start = System.currentTimeMillis();
        Map<String, Object> data = switch (targetService) {
            case "user-center" -> userCenterClient.invoke(toolName, params);
            case "account-center" -> accountCenterClient.invoke(toolName, params);
            case "business-center" -> businessCenterClient.invoke(toolName, params);
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
