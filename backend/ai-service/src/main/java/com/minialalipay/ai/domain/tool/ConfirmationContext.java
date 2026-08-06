package com.minialalipay.ai.domain.tool;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 可信确认上下文——由可信 UI 完成支付密码校验后生成，不进入 Agent 对话和 Prompt。
 *
 * <p>确认句柄 {@link #confirmationHandle} 是唯一标识，由策略网关在 MCP 调用时注入到 API 请求中。
 * 模型可见的工具参数仅包含业务引用（如 draftId），不含确认句柄。</p>
 *
 * <h3>安全不变量</h3>
 * <ul>
 *   <li>句柄一次性消费：同一句柄只能使用一次</li>
 *   <li>时效限制：默认 5 分钟有效</li>
 *   <li>约束锁定：确认上下文中锁定的金额、收款人等不可被覆盖</li>
 *   <li>主体绑定：仅生成句柄时的用户可以使用</li>
 * </ul>
 */
public class ConfirmationContext {

    /** 默认确认有效期（分钟） */
    public static final long DEFAULT_TTL_MINUTES = 5;

    private final String confirmationHandle;
    private final String principalId;
    private final String toolName;
    private final Map<String, Object> constraints;
    private final Instant expiresAt;
    private boolean consumed;

    /**
     * 创建确认上下文。
     *
     * @param principalId 用户主体 ID
     * @param toolName 待执行的高风险工具名
     * @param constraints 确认时锁定的约束（金额、收款人等）
     * @param ttlMinutes 有效期（分钟）
     */
    public ConfirmationContext(
            String principalId, String toolName,
            Map<String, Object> constraints, long ttlMinutes) {
        this.confirmationHandle = UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        this.principalId = Objects.requireNonNull(principalId, "主体 ID 不能为空");
        this.toolName = Objects.requireNonNull(toolName, "工具名不能为空");
        this.constraints = constraints != null ? new HashMap<>(constraints) : new HashMap<>();
        this.expiresAt = Instant.now().plusSeconds(ttlMinutes * 60);
        this.consumed = false;
    }

    /**
     * 校验并消费确认句柄。任一条件不满足即拒绝。
     *
     * @param principalId 当前请求主体
     * @param toolName 请求调用的工具名
     * @param now 当前时间
     * @throws IllegalStateException 句柄已消费、过期、主体不匹配或工具不匹配
     */
    public void consume(String principalId, String toolName, Instant now) {
        if (consumed) {
            throw new IllegalStateException("确认句柄已被消费，不可重复使用");
        }
        if (!this.principalId.equals(principalId)) {
            throw new IllegalStateException("确认句柄主体不匹配");
        }
        if (!this.toolName.equals(toolName)) {
            throw new IllegalStateException("确认句柄工具名不匹配");
        }
        if (now.isAfter(expiresAt)) {
            throw new IllegalStateException("确认句柄已过期");
        }
        this.consumed = true;
    }

    /**
     * 校验当前调用参数是否与确认时的约束一致。防止 LLM 在确认后修改关键参数。
     *
     * @param params 当前工具调用参数
     * @throws IllegalStateException 参数与确认约束不一致
     */
    public void validateConstraints(Map<String, Object> params) {
        for (Map.Entry<String, Object> entry : constraints.entrySet()) {
            String key = entry.getKey();
            Object expected = entry.getValue();
            Object actual = params.get(key);
            if (actual != null && !expected.equals(actual)) {
                throw new IllegalStateException(
                        "确认参数已被篡改: " + key + "=" + actual + "，期望: " + expected);
            }
        }
    }

    // ---- getters ----

    public String getConfirmationHandle() { return confirmationHandle; }
    public String getPrincipalId() { return principalId; }
    public String getToolName() { return toolName; }
    public Map<String, Object> getConstraints() { return Collections.unmodifiableMap(constraints); }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isConsumed() { return consumed; }
    public boolean isExpired(Instant now) { return now.isAfter(expiresAt); }
}
