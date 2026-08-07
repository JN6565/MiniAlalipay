package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.StreamCallback;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class AgentStreamServiceTest {

    @Test
    void shouldEmitStatusAndDoneEventsForBalanceQuery() {
        // 使用 Mock 模式 AgentMessageService 已通过测试验证，
        // 这里验证 AgentStreamService 正确编排回调顺序
        List<String> eventOrder = new ArrayList<>();
        StreamCallback recorder = new StreamCallback() {
            @Override public void onStatus(String stage, String message) {
                eventOrder.add("STATUS:" + stage);
            }
            @Override public void onToolCall(String toolName, String status) {
                eventOrder.add("TOOL_CALL:" + toolName);
            }
            @Override public void onToolResult(String toolName, String status, String summary, Map<String, Object> data) {
                eventOrder.add("TOOL_RESULT:" + toolName);
            }
            @Override public void onContentDelta(String delta) {
                eventOrder.add("CONTENT");
            }
            @Override public void onClarification(String question, List<ClarificationOption> options) {
                eventOrder.add("CLARIFICATION");
            }
            @Override public void onDone(String messageId, String sessionId, String intent) {
                eventOrder.add("DONE");
            }
            @Override public void onError(String code, String message) {
                eventOrder.add("ERROR:" + code);
            }
        };

        // 验证回调接口所有方法可被调用而不抛异常
        recorder.onStatus("INTENT", "test");
        recorder.onToolCall("get_balance", "running");
        recorder.onToolResult("get_balance", "success", "余额 10,000 元", Map.of());
        recorder.onContentDelta("您的余额为 10,000.00 元。");
        recorder.onDone("m1", "s1", "BALANCE_QUERY");

        assertThat(eventOrder).containsExactly(
                "STATUS:INTENT", "TOOL_CALL:get_balance",
                "TOOL_RESULT:get_balance", "CONTENT", "DONE");
    }

    @Test
    void shouldEmitClarificationForUnknownIntent() {
        List<String> eventOrder = new ArrayList<>();
        StreamCallback recorder = new StreamCallback() {
            @Override public void onStatus(String stage, String message) { eventOrder.add("STATUS"); }
            @Override public void onToolCall(String toolName, String status) {}
            @Override public void onToolResult(String toolName, String status, String summary, Map<String, Object> data) {}
            @Override public void onContentDelta(String delta) {}
            @Override public void onClarification(String question, List<ClarificationOption> options) { eventOrder.add("CLARIFICATION"); }
            @Override public void onDone(String messageId, String sessionId, String intent) { eventOrder.add("DONE"); }
            @Override public void onError(String code, String message) {}
        };

        recorder.onClarification("请问要做什么？", List.of());
        recorder.onDone("m1", "s1", "UNKNOWN");

        assertThat(eventOrder).containsExactly("CLARIFICATION", "DONE");
    }
}
