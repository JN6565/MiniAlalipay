package com.minialalipay.ai.infrastructure.client;

import com.minialalipay.ai.domain.agent.AgentErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 下游 4xx 错误透传测试。
 *
 * <p>保护不变量：下游业务错误（余额不足、超限等）必须把下游中文 message 透传给用户，
 * 而不是被掩盖为"服务暂不可用"；仅响应体不可解析时才回退 TOOL_UNAVAILABLE。</p>
 */
class DownstreamErrorTranslatorTest {

    @Test
    void shouldPassThroughDownstreamBusinessMessage() {
        // 模拟下游 ApiResponse 错误信封：业务错误码 + 中文文案
        String body = "{\"success\":false,\"code\":\"INSUFFICIENT_BALANCE\",\"message\":\"账户余额不足\"}";
        MockClientHttpResponse response = new MockClientHttpResponse(
                body.getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST);

        BusinessException exception = DownstreamErrorTranslator.translate("business-center", response);

        // 异常文案即下游中文 message，经 ToolRouter/ResultInterpreter 原样展示给用户
        assertThat(exception.getMessage()).isEqualTo("账户余额不足");
        assertThat(exception.errorCode()).isInstanceOf(DownstreamBusinessError.class);
        assertThat(exception.errorCode().code()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(exception.errorCode().httpStatus()).isEqualTo(400);
    }

    @Test
    void shouldFallbackToToolUnavailableWhenBodyUnparseable() {
        MockClientHttpResponse response = new MockClientHttpResponse(
                "not-json".getBytes(StandardCharsets.UTF_8), HttpStatus.BAD_REQUEST);

        BusinessException exception = DownstreamErrorTranslator.translate("business-center", response);

        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.TOOL_UNAVAILABLE);
    }

    @Test
    void shouldFallbackToToolUnavailableWhenMessageMissing() {
        String body = "{\"success\":false,\"code\":\"SOME_ERROR\"}";
        MockClientHttpResponse response = new MockClientHttpResponse(
                body.getBytes(StandardCharsets.UTF_8), HttpStatus.UNPROCESSABLE_ENTITY);

        BusinessException exception = DownstreamErrorTranslator.translate("account-center", response);

        assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.TOOL_UNAVAILABLE);
    }
}
