package com.minialalipay.gateway.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 网关审计日志输出器测试。
 *
 * <p>验证各事件类型的日志输出不抛出异常，以及敏感字段长度截断。 </p>
 */
class GatewayAuditLoggerTest {

    private final GatewayAuditLogger logger = new GatewayAuditLogger();

    @Test
    @DisplayName("正常审计事件日志输出不抛出异常")
    void normalAuditEventDoesNotThrow() {
        assertThatCode(() -> logger.log(
                AuditEvent.AUTH_MISSING_TOKEN,
                "req-001", "trace-001", "-",
                "/api/v1/transfers", "POST",
                "缺少Authorization头"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("拒绝事件日志输出不抛出异常")
    void rejectionEventDoesNotThrow() {
        assertThatCode(() -> logger.logRejection(
                AuditEvent.CSRF_REJECTED,
                "req-002", "trace-002", "dev-user-001",
                "/api/v1/transfers", "POST",
                "CSRF Token 缺失",
                "Cookie会话写请求缺少有效CSRF Token"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null 参数不导致空指针异常")
    void nullParametersDoNotThrowNullPointer() {
        assertThatCode(() -> logger.log(
                AuditEvent.AUTH_INVALID_TOKEN,
                null, null, null,
                null, null, null))
                .doesNotThrowAnyException();

        assertThatCode(() -> logger.logRejection(
                AuditEvent.RATE_LIMIT_TRIGGERED,
                null, null, null,
                null, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("超长 detail 自动截断不抛出异常")
    void overlyLongDetailIsTruncated() {
        String longDetail = "x".repeat(500);
        assertThatCode(() -> logger.log(
                AuditEvent.AUTHORIZATION_DENIED,
                "req-003", "trace-003", "dev-user-001",
                "/api/v1/ops/credit/status", "GET",
                longDetail))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("超长 reason 自动截断不抛出异常")
    void overlyLongReasonIsTruncated() {
        String longReason = "y".repeat(300);
        assertThatCode(() -> logger.logRejection(
                AuditEvent.CSRF_REJECTED,
                "req-004", "trace-004", "-",
                "/api/v1/transfers", "POST",
                longReason, "test"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("所有审计事件类型均可记录")
    void allAuditEventTypesAreLoggable() {
        for (AuditEvent event : AuditEvent.values()) {
            assertThatCode(() -> logger.log(
                    event, "req-all", "trace-all", "dev-user-001",
                    "/api/v1/test", "POST", "批量测试"))
                    .doesNotThrowAnyException();
        }
    }
}
