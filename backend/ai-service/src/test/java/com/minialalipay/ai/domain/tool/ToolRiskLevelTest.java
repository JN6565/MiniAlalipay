package com.minialalipay.ai.domain.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolRiskLevelTest {

    @Test
    void onlyHighRiskToolRequiresTrustedConfirmation() {
        assertThat(ToolRiskLevel.HIGH.requiresConfirmation()).isTrue();
        assertThat(ToolRiskLevel.LOW.requiresConfirmation()).isFalse();
        assertThat(ToolRiskLevel.READ_ONLY.requiresConfirmation()).isFalse();
    }
}
