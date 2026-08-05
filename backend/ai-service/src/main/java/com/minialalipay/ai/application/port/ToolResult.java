package com.minialalipay.ai.application.port;

import java.util.Map;

/**
 * 工具调用结果，由 ToolRouter 返回给应用层。
 *
 * @param resultCode 标准工具结果码，如 SUCCESS/USER_NOT_FOUND/TOOL_UNAVAILABLE
 * @param data 工具返回的脱敏数据，不包含支付密码、令牌或确认上下文
 * @param errorMessage 失败时的中文错误提示
 * @param durationMs 工具调用耗时（毫秒），用于审计
 */
public record ToolResult(
        String resultCode,
        Map<String, Object> data,
        String errorMessage,
        int durationMs
) {
}
