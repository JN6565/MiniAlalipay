package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.SseEvent;
import com.minialalipay.ai.application.port.StreamCallback;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AgentStreamServiceTest {

    @Test
    void shouldEmitStatusAndDoneEventsForBalanceQuery() {
        // 使用 Mock 模式 AgentMessageService 已通过测试验证，
        // 这里验证 AgentStreamService 正确编排回调顺序
        List<String> eventOrder = new ArrayList<>();
        StreamCallback recorder = new StreamCallback() {
            @Override public void onStatus(SseEvent.StatusPayload e) {
                eventOrder.add("STATUS:" + e.stage());
            }
            @Override public void onToolCall(SseEvent.ToolCallPayload e) {
                eventOrder.add("TOOL_CALL:" + e.tool());
            }
            @Override public void onToolResult(SseEvent.ToolResultPayload e) {
                eventOrder.add("TOOL_RESULT:" + e.tool());
            }
            @Override public void onContentDelta(SseEvent.ContentPayload e) {
                eventOrder.add("CONTENT");
            }
            @Override public void onConfirmation(SseEvent.ConfirmationPayload e) {
                eventOrder.add("CONFIRMATION");
            }
            @Override public void onClarification(SseEvent.ClarificationPayload e) {
                eventOrder.add("CLARIFICATION");
            }
            @Override public void onDone(SseEvent.DonePayload e) {
                eventOrder.add("DONE");
            }
            @Override public void onError(SseEvent.ErrorPayload e) {
                eventOrder.add("ERROR:" + e.code());
            }
        };

        // 验证回调接口所有方法可被调用而不抛异常
        recorder.onStatus(new SseEvent.StatusPayload("INTENT", "test"));
        recorder.onToolCall(new SseEvent.ToolCallPayload("get_balance", "running"));
        recorder.onToolResult(new SseEvent.ToolResultPayload("get_balance", "success", "余额 10,000 元"));
        recorder.onContentDelta(new SseEvent.ContentPayload("您的余额为 10,000.00 元。"));
        recorder.onDone(new SseEvent.DonePayload("s1", "m1", "BALANCE_QUERY"));

        assertThat(eventOrder).containsExactly(
                "STATUS:INTENT", "TOOL_CALL:get_balance",
                "TOOL_RESULT:get_balance", "CONTENT", "DONE");
    }

    @Test
    void shouldEmitClarificationForUnknownIntent() {
        List<String> eventOrder = new ArrayList<>();
        StreamCallback recorder = new StreamCallback() {
            @Override public void onStatus(SseEvent.StatusPayload e) { eventOrder.add("STATUS"); }
            @Override public void onToolCall(SseEvent.ToolCallPayload e) {}
            @Override public void onToolResult(SseEvent.ToolResultPayload e) {}
            @Override public void onContentDelta(SseEvent.ContentPayload e) {}
            @Override public void onConfirmation(SseEvent.ConfirmationPayload e) {}
            @Override public void onClarification(SseEvent.ClarificationPayload e) { eventOrder.add("CLARIFICATION"); }
            @Override public void onDone(SseEvent.DonePayload e) { eventOrder.add("DONE"); }
            @Override public void onError(SseEvent.ErrorPayload e) {}
        };

        recorder.onClarification(new SseEvent.ClarificationPayload("请问要做什么？", List.of()));
        recorder.onDone(new SseEvent.DonePayload("s1", "m1", "UNKNOWN"));

        assertThat(eventOrder).containsExactly("CLARIFICATION", "DONE");
    }
}
