package com.minialalipay.ai.domain.tool;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ToolCallLog 实体单元测试。
 *
 * <p>验证工具调用日志的创建不变量：摘要长度、耗时非负、必填字段校验。</p>
 */
class ToolCallLogTest {

    private static final String TOOL_CALL_ID = "01J5Q000000000000000000020";
    private static final String SESSION_ID = "01J5Q000000000000000000001";
    private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

    @Test
    void shouldCreateToolCallLog() {
        byte[] digest = new byte[32];
        ToolCallLog log = new ToolCallLog(
                TOOL_CALL_ID, SESSION_ID, "get_balance",
                digest, "SUCCESS", 150, "abcdef0123456789abcdef0123456789", NOW);

        assertThat(log.getToolCallId()).isEqualTo(TOOL_CALL_ID);
        assertThat(log.getToolName()).isEqualTo("get_balance");
        assertThat(log.getResultCode()).isEqualTo("SUCCESS");
        assertThat(log.getDurationMs()).isEqualTo(150);
        assertThat(log.getTraceId()).hasSize(32);
    }

    @Test
    void shouldRejectInvalidDigestLength() {
        byte[] shortDigest = new byte[16];

        assertThatThrownBy(() -> new ToolCallLog(
                TOOL_CALL_ID, SESSION_ID, "get_balance",
                shortDigest, "SUCCESS", 150,
                "abcdef0123456789abcdef0123456789", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 字节");
    }

    @Test
    void shouldRejectNegativeDuration() {
        byte[] digest = new byte[32];

        assertThatThrownBy(() -> new ToolCallLog(
                TOOL_CALL_ID, SESSION_ID, "get_balance",
                digest, "SUCCESS", -1,
                "abcdef0123456789abcdef0123456789", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("耗时");
    }

    @Test
    void requestDigestShouldReturnDefensiveCopy() {
        byte[] original = new byte[32];
        original[0] = 0x01;
        ToolCallLog log = new ToolCallLog(
                TOOL_CALL_ID, SESSION_ID, "get_balance",
                original, "SUCCESS", 100,
                "abcdef0123456789abcdef0123456789", NOW);

        byte[] copy = log.getRequestDigest();
        copy[0] = 0x02; // 修改副本

        // 原始摘要不受影响
        assertThat(log.getRequestDigest()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void shouldRejectNullTraceId() {
        byte[] digest = new byte[32];

        assertThatThrownBy(() -> new ToolCallLog(
                TOOL_CALL_ID, SESSION_ID, "get_balance",
                digest, "SUCCESS", 100, null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Trace ID");
    }
}
