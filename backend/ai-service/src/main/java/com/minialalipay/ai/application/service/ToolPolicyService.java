package com.minialalipay.ai.application.service;

import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.ai.domain.agent.AgentSession;
import com.minialalipay.ai.domain.tool.ConfirmationContext;
import com.minialalipay.ai.domain.tool.ToolCatalog;
import com.minialalipay.ai.domain.tool.ToolRiskLevel;
import com.minialalipay.common.error.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 工具策略网关服务（阶段五：确认上下文 + 越权拦截）。
 *
 * <p>按四级风险等级进行访问控制，高风险资金工具必须携带有效确认上下文。</p>
 *
 * <h3>越权拦截</h3>
 * <ul>
 *   <li>模型参数中的 userId 被忽略，始终使用服务端派生的主体身份</li>
 *   <li>模型参数尝试覆盖付款账户、收款账户、确认信息或资金来源时立即拒绝</li>
 *   <li>工具返回内容不可覆盖系统指令</li>
 * </ul>
 */
@Service
public class ToolPolicyService {

    private static final Logger log = LoggerFactory.getLogger(ToolPolicyService.class);

    /** 模型不得通过参数覆盖的受保护字段 */
    private static final List<String> PROTECTED_PARAMS = List.of(
            "userId", "principalId", "fundingSource",
            "paymentPassword", "confirmationToken", "confirmationHandle",
            "payerAccountId", "payeeAccountId"
    );

    private final ToolCatalog toolCatalog;
    private final Clock clock;

    /** 活跃的确认上下文存储（阶段五使用内存实现，生产应迁移到 Redis） */
    private final ConcurrentHashMap<String, ConfirmationContext> confirmationStore
            = new ConcurrentHashMap<>();

    public ToolPolicyService(ToolCatalog toolCatalog, Clock clock) {
        this.toolCatalog = toolCatalog;
        this.clock = clock;
    }

    /**
     * 评估工具调用是否被允许（不含确认上下文）。
     */
    public PolicyDecision evaluate(String toolName, AgentSession session) {
        ToolCatalog.ToolDefinition tool = toolCatalog.lookup(toolName)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.TOOL_UNAVAILABLE));

        if (!session.isActive()) {
            return new PolicyDecision(false, tool.riskLevel(),
                    "会话已失效，请重新开始对话", false);
        }

        return switch (tool.riskLevel()) {
            case READ_ONLY -> new PolicyDecision(true, tool.riskLevel(), null, false);
            case DRAFT -> new PolicyDecision(true, tool.riskLevel(),
                    "草稿工具需要幂等键和版本 CAS 校验", false);
            case VALIDATION -> new PolicyDecision(true, tool.riskLevel(),
                    "校验工具不得产生资金副作用", false);
            case HIGH_RISK_WRITE -> new PolicyDecision(false, tool.riskLevel(),
                    "高风险资金操作需要先在 UI 中完成支付密码确认", true);
        };
    }

    /**
     * 带确认上下文的高风险工具评估。
     *
     * @param toolName 工具名
     * @param session 会话
     * @param params 工具调用参数（由 LLM 生成，不可信）
     * @param confirmationHandle 确认句柄（由可信 UI 产生，服务端注入，模型不可见）
     * @return 策略决策
     */
    public PolicyDecision evaluateWithConfirmation(
            String toolName, AgentSession session,
            Map<String, Object> params, String confirmationHandle) {

        // 先做基础评估
        PolicyDecision base = evaluate(toolName, session);
        if (!base.needsConfirmationContext()) {
            return base;
        }

        // 越权拦截：拒绝受保护字段的篡改尝试
        for (String protectedParam : PROTECTED_PARAMS) {
            if (params.containsKey(protectedParam)) {
                log.warn("越权拦截：模型尝试覆盖受保护字段 {}={}",
                        protectedParam, params.get(protectedParam));
                throw new BusinessException(AgentErrorCode.PROMPT_INJECTION_REJECTED);
            }
        }

        // 确认上下文校验
        if (confirmationHandle == null || confirmationHandle.isBlank()) {
            return new PolicyDecision(false, ToolRiskLevel.HIGH_RISK_WRITE,
                    "高风险资金操作需要先在 UI 中完成支付密码确认", true);
        }

        ConfirmationContext ctx = confirmationStore.get(confirmationHandle);
        if (ctx == null) {
            return new PolicyDecision(false, ToolRiskLevel.HIGH_RISK_WRITE,
                    "确认句柄无效，请重新完成支付密码确认", true);
        }

        Instant now = clock.instant();
        if (ctx.isExpired(now)) {
            confirmationStore.remove(confirmationHandle);
            return new PolicyDecision(false, ToolRiskLevel.HIGH_RISK_WRITE,
                    "确认句柄已过期，请重新确认", true);
        }

        if (ctx.isConsumed()) {
            return new PolicyDecision(false, ToolRiskLevel.HIGH_RISK_WRITE,
                    "确认句柄已被使用，不可重复消费", true);
        }

        // 参数一致性校验：确认时锁定的金额/收款人等不可被 LLM 修改
        try {
            ctx.validateConstraints(params);
        } catch (IllegalStateException e) {
            log.warn("确认约束校验失败: {}", e.getMessage());
            throw new BusinessException(AgentErrorCode.PROMPT_INJECTION_REJECTED);
        }

        // 消费并移除（一锤子买卖）
        ctx.consume(ctx.getPrincipalId(), toolName, now);
        confirmationStore.remove(confirmationHandle);

        log.info("高风险工具确认通过: tool={}, principalId={}",
                toolName, ctx.getPrincipalId());
        return new PolicyDecision(true, ToolRiskLevel.HIGH_RISK_WRITE,
                "确认通过，允许执行", false);
    }

    /**
     * 注册确认上下文（可信 UI 完成支付密码校验后的回调）。
     */
    public void registerConfirmation(ConfirmationContext ctx) {
        confirmationStore.put(ctx.getConfirmationHandle(), ctx);
        log.info("确认上下文已注册: handle={}, tool={}, expiresAt={}",
                ctx.getConfirmationHandle(), ctx.getToolName(), ctx.getExpiresAt());
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
