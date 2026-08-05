package com.minialalipay.ai.application.service;

import com.minialalipay.ai.domain.agent.AgentSession;
import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.domain.tool.ToolRiskLevel;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.common.error.BusinessException;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * MCP 工具策略网关服务。
 *
 * <p>按四级风险等级对工具调用进行访问控制：
 * 查询工具需登录，草稿工具额外需幂等和版本，
 * 校验工具不得产生资金副作用，高风险资金工具默认不可执行。</p>
 *
 * <h3>策略规则</h3>
 * <ul>
 *   <li>{@link ToolRiskLevel#READ_ONLY}：需登录（会话 ACTIVE）</li>
 *   <li>{@link ToolRiskLevel#DRAFT}：需登录 + 幂等 + 版本 CAS</li>
 *   <li>{@link ToolRiskLevel#VALIDATION}：需登录，不得产生资金副作用</li>
 *   <li>{@link ToolRiskLevel#HIGH_RISK_WRITE}：默认不可执行，需可信 UI 确认上下文</li>
 * </ul>
 */
@Service
public class ToolPolicyService {

    private final ToolCatalog toolCatalog;

    public ToolPolicyService(ToolCatalog toolCatalog) {
        this.toolCatalog = toolCatalog;
    }

    /**
     * 评估工具调用是否被允许。
     *
     * @param toolName 工具契约名称
     * @param session 发起调用的会话（需为 ACTIVE）
     * @return 策略决策结果
     * @throws BusinessException 当工具不存在或会话不可用时
     */
    public PolicyDecision evaluate(String toolName, AgentSession session) {
        ToolCatalog.ToolDefinition tool = toolCatalog.lookup(toolName)
                .orElseThrow(() -> {
                    throw new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE);
                });

        // 基础检查：会话必须是 ACTIVE
        if (!session.isActive()) {
            return new PolicyDecision(false, tool.riskLevel(),
                    "会话已失效，请重新开始对话", false);
        }

        // 按风险等级分级决策
        return switch (tool.riskLevel()) {
            case READ_ONLY -> new PolicyDecision(true, tool.riskLevel(), null, false);

            case DRAFT -> new PolicyDecision(true, tool.riskLevel(),
                    "草稿工具需要幂等键和版本 CAS 校验", false);

            case VALIDATION -> new PolicyDecision(true, tool.riskLevel(),
                    "校验工具不得产生资金副作用", false);

            case HIGH_RISK_WRITE -> evaluateHighRiskWrite(session, tool);
        };
    }

    private PolicyDecision evaluateHighRiskWrite(
            AgentSession session, ToolCatalog.ToolDefinition tool) {
        // 阶段四：高风险资金工具一律 fail-closed。
        // 确认令牌和确认上下文禁止进入会话槽位、Prompt、消息或数据库。
        // 真实可信确认注入、消费和提交由阶段五实现。
        return new PolicyDecision(false, tool.riskLevel(),
                "高风险资金操作需要先在 UI 中完成支付密码确认", true);
    }

    /**
     * 工具策略决策结果。
     */
    public record PolicyDecision(
            boolean allowed,
            ToolRiskLevel effectiveRiskLevel,
            String reason,
            boolean needsConfirmationContext
    ) {
    }
}
