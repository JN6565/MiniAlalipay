package com.minialalipay.ai.domain.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentErrorCode 契约测试：验证枚举值与 contracts/error-codes/error-codes.yaml 一致。
 */
class AgentErrorCodeContractTest {

    @Test
    void agentBusyShouldMapTo409() {
        assertThat(AgentErrorCode.AGENT_BUSY.code()).isEqualTo("AGENT_BUSY");
        assertThat(AgentErrorCode.AGENT_BUSY.message()).isEqualTo("当前会话正在处理上一条消息");
        assertThat(AgentErrorCode.AGENT_BUSY.httpStatus()).isEqualTo(409);
    }

    @Test
    void sessionNotFoundShouldMapTo404() {
        assertThat(AgentErrorCode.SESSION_NOT_FOUND.code()).isEqualTo("SESSION_NOT_FOUND");
        assertThat(AgentErrorCode.SESSION_NOT_FOUND.message()).isEqualTo("AI 会话不存在");
        assertThat(AgentErrorCode.SESSION_NOT_FOUND.httpStatus()).isEqualTo(404);
    }

    @Test
    void promptInjectionRejectedShouldMapTo400() {
        assertThat(AgentErrorCode.PROMPT_INJECTION_REJECTED.code())
                .isEqualTo("PROMPT_INJECTION_REJECTED");
        assertThat(AgentErrorCode.PROMPT_INJECTION_REJECTED.message())
                .isEqualTo("请求包含不安全内容，已被拒绝");
        assertThat(AgentErrorCode.PROMPT_INJECTION_REJECTED.httpStatus()).isEqualTo(400);
    }

    @Test
    void toolUnavailableShouldMapTo503() {
        assertThat(AgentErrorCode.TOOL_UNAVAILABLE.code()).isEqualTo("TOOL_UNAVAILABLE");
        assertThat(AgentErrorCode.TOOL_UNAVAILABLE.message()).isEqualTo("工具服务暂不可用");
        assertThat(AgentErrorCode.TOOL_UNAVAILABLE.httpStatus()).isEqualTo(503);
    }

    @Test
    void shouldHaveAllRequiredErrorCodes() {
        // 阶段四应有 AGENT_BUSY, SESSION_NOT_FOUND, AGENT_SESSION_EXPIRED,
        // INTENT_LOW_CONFIDENCE, TOOL_FORBIDDEN, TOOL_UNAVAILABLE,
        // PROMPT_INJECTION_REJECTED, IDEMPOTENCY_CONFLICT, VERSION_CONFLICT,
        // LLM_UNAVAILABLE 共 10 个错误码
        assertThat(AgentErrorCode.values()).hasSize(10);
    }
}
