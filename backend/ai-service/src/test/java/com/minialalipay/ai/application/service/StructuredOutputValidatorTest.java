package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.port.ChatResponse;
import com.minialalipay.ai.domain.agent.IntentType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class StructuredOutputValidatorTest {

    private final StructuredOutputValidator validator = new StructuredOutputValidator();

    @Test
    void shouldParseValidJsonAndMapToChatResponse() {
        String llmOutput = """
                ```json
                {
                  "intent": "TRANSFER",
                  "slots": {"payeeQuery": "张三", "amountFen": 10000},
                  "missingFields": ["payeeId"],
                  "confidence": 0.91,
                  "clarificationNeeded": true,
                  "naturalReply": "请问转给哪位张三？"
                }
                ```""";

        StructuredOutputValidator.ValidatedResponse result = validator.validate(llmOutput);

        assertThat(result.rawJson()).contains("TRANSFER");
        ChatResponse cr = result.chatResponse();
        assertThat(cr.intent()).isEqualTo(IntentType.TRANSFER);
        assertThat(cr.slots()).containsEntry("amountFen", 10000L);
        assertThat(cr.clarificationNeeded()).isTrue();
        assertThat(cr.content()).isEqualTo("请问转给哪位张三？");
    }

    @Test
    void shouldRejectNonJsonOutput() {
        String llmOutput = "好的，我帮您转账，请输入金额。";

        assertThatThrownBy(() -> validator.validate(llmOutput))
                .hasMessageContaining("JSON");
    }

    @Test
    void shouldRejectUnknownIntent() {
        String json = """
                {"intent":"FLY_TO_MOON","slots":{},"missingFields":[],"confidence":1.0,"clarificationNeeded":false,"naturalReply":"起飞"}""";

        assertThatThrownBy(() -> validator.validate(json))
                .hasMessageContaining("intent");
    }

    @Test
    void shouldHandlePlainJsonWithoutMarkdownWrapper() {
        String json = """
                {"intent":"BALANCE_QUERY","slots":{},"missingFields":[],"confidence":0.95,"clarificationNeeded":false,"naturalReply":"余额为10,000.00元"}""";

        StructuredOutputValidator.ValidatedResponse result = validator.validate(json);

        assertThat(result.chatResponse().intent()).isEqualTo(IntentType.BALANCE_QUERY);
    }

    @Test
    void shouldRejectExtraPropertiesDueToAdditionalPropertiesFalse() {
        String json = """
                {"intent":"TRANSFER","slots":{},"missingFields":[],"confidence":1.0,"clarificationNeeded":false,"naturalReply":"ok","password":"123456"}""";

        assertThatThrownBy(() -> validator.validate(json))
                .hasMessageContaining("additionalProperties");
    }
}
