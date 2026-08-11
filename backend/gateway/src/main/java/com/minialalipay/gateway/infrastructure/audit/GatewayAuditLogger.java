package com.minialalipay.gateway.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 网关安全审计日志输出器。
 *
 * <p>将接入层安全事件以结构化格式写入日志，供审计、告警和故障回溯使用。
 * 每一条审计日志包含事件类型、请求标识、主体标识和关键上下文，
 * 不包含密码、令牌原文、Cookie 或完整账号。</p>
 *
 * <h3>日志格式约定</h3>
 * <p>使用 SLF4J 参数化日志，关键字段以 {@code key=value} 形式输出，
 * 便于日志平台解析。身份字段使用脱敏或内部标识。</p>
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>禁止记录 Authorization 头、Cookie、X-CSRF-Token 原文</li>
 *   <li>禁止记录请求体或查询参数中的敏感字段</li>
 *   <li>审计日志失败不得改变业务结果</li>
 * </ul>
 */
@Component
public class GatewayAuditLogger {

    private static final Logger AUDIT = LoggerFactory.getLogger("GATEWAY_AUDIT");

    /**
     * 记录一条安全审计事件。
     *
     * @param event       事件类型
     * @param requestId   请求编号
     * @param traceId     链路编号
     * @param principalId 认证主体标识，未认证时为 "-"
     * @param path        请求路径
     * @param method      HTTP 方法
     * @param detail      脱敏补充说明，不超过 200 字符
     */
    public void log(AuditEvent event,
                    String requestId,
                    String traceId,
                    String principalId,
                    String path,
                    String method,
                    String detail) {
        String safePrincipal = principalId != null ? principalId : "-";
        String safeDetail = truncate(detail, 200);
        AUDIT.info("event={} requestId={} traceId={} principal={} method={} path={} detail={}",
                event.name(),
                nullToEmpty(requestId),
                nullToEmpty(traceId),
                safePrincipal,
                nullToEmpty(method),
                nullToEmpty(path),
                safeDetail);
    }

    /**
     * 记录拒绝类事件（WARN 级别），附加拒绝原因分类。
     *
     * @param event       事件类型
     * @param requestId   请求编号
     * @param traceId     链路编号
     * @param principalId 认证主体标识
     * @param path        请求路径
     * @param method      HTTP 方法
     * @param reason      拒绝原因分类
     * @param detail      脱敏补充说明
     */
    public void logRejection(AuditEvent event,
                             String requestId,
                             String traceId,
                             String principalId,
                             String path,
                             String method,
                             String reason,
                             String detail) {
        String safePrincipal = principalId != null ? principalId : "-";
        String safeReason = truncate(reason, 100);
        String safeDetail = truncate(detail, 200);
        AUDIT.warn("event={} requestId={} traceId={} principal={} method={} path={} reason={} detail={}",
                event.name(),
                nullToEmpty(requestId),
                nullToEmpty(traceId),
                safePrincipal,
                nullToEmpty(method),
                nullToEmpty(path),
                safeReason,
                safeDetail);
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…";
    }
}
