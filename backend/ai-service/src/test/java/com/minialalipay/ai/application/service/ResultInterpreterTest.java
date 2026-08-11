package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 结果解释引擎测试：验证状态码到中文解释的映射准确性。
 *
 * <p>核心规则：PROCESSING/COMPENSATING/MANUAL_REVIEW 不得被解释为成功。</p>
 */
class ResultInterpreterTest {

    private final ResultInterpreter interpreter = new ResultInterpreter();

    @Test
    void shouldNotInterpretProcessingAsSuccess() {
        ToolResult result = new ToolResult("SUCCESS",
                Map.of("status", "PROCESSING", "transactionId", "txn-001"),
                null, 150);
        String explanation = interpreter.interpret("submit_confirmed_transfer", result);
        assertThat(explanation).contains("处理");
        assertThat(explanation).doesNotContain("成功");
    }

    @Test
    void shouldNotInterpretCompensatingAsSuccess() {
        ToolResult result = new ToolResult("SUCCESS",
                Map.of("status", "COMPENSATING"),
                null, 200);
        String explanation = interpreter.interpret("submit_confirmed_transfer", result);
        assertThat(explanation).contains("异常恢复");
        assertThat(explanation).doesNotContain("成功");
    }

    @Test
    void shouldNotInterpretManualReviewAsSuccess() {
        ToolResult result = new ToolResult("SUCCESS",
                Map.of("status", "MANUAL_REVIEW"),
                null, 300);
        String explanation = interpreter.interpret("submit_confirmed_transfer", result);
        assertThat(explanation).contains("人工审核");
        assertThat(explanation).doesNotContain("成功");
    }

    @Test
    void shouldReportSuccessWhenStatusIsSuccess() {
        ToolResult result = new ToolResult("SUCCESS",
                Map.of("status", "SUCCESS", "transactionId", "txn-001"),
                null, 100);
        String explanation = interpreter.interpret("submit_confirmed_transfer", result);
        assertThat(explanation).contains("成功");
    }

    @Test
    void shouldGuideWhenInsufficientBalance() {
        ToolResult result = new ToolResult("SUCCESS",
                Map.of("status", "FAILED", "reason", "INSUFFICIENT_BALANCE"),
                null, 50);
        String explanation = interpreter.interpret("submit_confirmed_transfer", result);
        assertThat(explanation).contains("余额不足");
        assertThat(explanation).doesNotContain("充值");
    }

    @Test
    void shouldFormatAmountCorrectly() {
        assertThat(ResultInterpreter.formatFen(10000L)).isEqualTo("100.00");
        assertThat(ResultInterpreter.formatFen(0L)).isEqualTo("0.00");
        assertThat(ResultInterpreter.formatFen(500000L)).isEqualTo("5,000.00");
    }

    @Test
    void shouldReturnFallbackWhenToolUnavailable() {
        ToolResult result = new ToolResult("TOOL_UNAVAILABLE",
                Map.of(), "连接超时", 3000);
        String explanation = interpreter.interpret("get_balance", result);
        assertThat(explanation).contains("无法查询");
    }

    @Test
    void shouldReturnFallbackWhenResultIsNull() {
        String explanation = interpreter.interpret("get_balance", null);
        assertThat(explanation).contains("暂不可用");
    }

    @Test
    void shouldPassThroughDownstreamBusinessErrorMessage() {
        // ToolRouter 对下游 4xx 业务错误返回 BUSINESS_ERROR + 下游中文 message，
        // 解释引擎必须原样透传，而不是降级为"服务暂不可用"
        ToolResult result = new ToolResult("BUSINESS_ERROR",
                Map.of(), "账户余额不足", 120);
        String explanation = interpreter.interpret("submit_confirmed_transfer", result);
        assertThat(explanation).isEqualTo("账户余额不足");
        assertThat(explanation).doesNotContain("暂不可用");
    }

    @Test
    void shouldNotInventBalanceWhenRequiredFieldIsMissing() {
        ToolResult result = new ToolResult("SUCCESS", Map.of(), null, 10);

        String explanation = interpreter.interpret("get_balance", result);

        assertThat(explanation).contains("数据不完整");
        assertThat(explanation).doesNotContain("0.00");
    }

    @Test
    void shouldNotInventCreditLimitWhenRequiredFieldsAreMissing() {
        ToolResult result = new ToolResult("SUCCESS", Map.of("usedFen", 100L), null, 10);

        String explanation = interpreter.interpret("get_credit_summary", result);

        assertThat(explanation).contains("数据不完整");
        assertThat(explanation).doesNotContain("5,000.00");
    }

    @Test
    void shouldNotClaimNoTransactionsWhenItemsFieldIsMissing() {
        ToolResult result = new ToolResult("SUCCESS", Map.of(), null, 10);

        String explanation = interpreter.interpret("list_transactions", result);

        assertThat(explanation).contains("数据不完整");
        assertThat(explanation).doesNotContain("暂无交易");
    }
}
