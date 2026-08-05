package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.application.port.ToolResult;
import com.minialalipay.ai.application.service.ToolPolicyService;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.domain.agent.AgentSession;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MCP 工具适配器。
 *
 * <p>在应用启动时加载工具目录，对外暴露已批准的工具 Schema。
 * 调用时依次经过：工具白名单查询 → 策略网关校验 → 路由分发。
 * 高风险工具的 Schema 不含支付密码、确认令牌或确认句柄。</p>
 */
@Component
public class McpToolAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    private final ToolCatalog toolCatalog;
    private final ToolPolicyService toolPolicyService;
    private final ToolRouter toolRouter;

    public McpToolAdapter(
            ToolCatalog toolCatalog,
            ToolPolicyService toolPolicyService,
            ToolRouter toolRouter
    ) {
        this.toolCatalog = toolCatalog;
        this.toolPolicyService = toolPolicyService;
        this.toolRouter = toolRouter;
        log.info("MCP 工具适配器初始化完成，已加载 {} 个工具", toolCatalog.allTools().size());
    }

    /**
     * 获取当前可用的工具清单（仅含已被批准的工具 Schema）。
     *
     * @return 工具定义列表，不含高风险工具的确认令牌参数
     */
    public List<McpToolInfo> listAvailableTools() {
        return toolCatalog.allTools().entrySet().stream()
                .map(e -> new McpToolInfo(
                        e.getKey(),
                        e.getValue().description(),
                        e.getValue().riskLevel().name()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 调用指定工具。
     *
     * @param toolName 工具契约名称
     * @param params 工具参数（由 LLM 生成，不可信）
     * @param session 发起调用的会话
     * @param userId 用户 ID
     * @return 工具执行结果
     * @throws BusinessException 工具不存在、策略拒绝或调用失败时
     */
    public ToolResult invokeTool(
            String toolName, Map<String, Object> params,
            AgentSession session, String userId
    ) {
        // 1. 策略校验
        ToolPolicyService.PolicyDecision decision = toolPolicyService.evaluate(toolName, session);
        if (!decision.allowed()) {
            log.warn("工具调用被策略拒绝: tool={}, reason={}", toolName, decision.reason());
            throw new BusinessException(
                    decision.needsConfirmationContext()
                            ? AgentErrorCode.PROMPT_INJECTION_REJECTED
                            : AgentErrorCode.TOOL_UNAVAILABLE
            );
        }

        // 2. 路由执行
        ToolCatalog.ToolDefinition def = toolCatalog.lookup(toolName)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE));
        boolean retryIfReadOnly = def.riskLevel()
                == com.minialalipay.ai.domain.tool.ToolRiskLevel.READ_ONLY;

        return toolRouter.route(toolName, params, userId, retryIfReadOnly);
    }

    /**
     * MCP 工具信息，对外暴露的工具摘要。
     */
    public record McpToolInfo(String name, String description, String riskLevel) {
    }
}
