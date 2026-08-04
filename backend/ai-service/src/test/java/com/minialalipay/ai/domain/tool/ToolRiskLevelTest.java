package com.minialalipay.ai.domain.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具风险等级枚举单元测试。
 *
 * <p>验证风险分级的 {@code requiresConfirmation()} 和 {@code allowsSideEffects()} 逻辑，
 * 确保只有 HIGH_RISK_WRITE 需要确认、只有 READ_ONLY 不产生副作用。</p>
 */
class ToolRiskLevelTest {

    @Test
    void onlyHighRiskWriteRequiresTrustedConfirmation() {
        assertThat(ToolRiskLevel.HIGH_RISK_WRITE.requiresConfirmation()).isTrue();
        assertThat(ToolRiskLevel.READ_ONLY.requiresConfirmation()).isFalse();
        assertThat(ToolRiskLevel.DRAFT.requiresConfirmation()).isFalse();
        assertThat(ToolRiskLevel.VALIDATION.requiresConfirmation()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ToolRiskLevel.class, names = {"DRAFT", "VALIDATION", "HIGH_RISK_WRITE"})
    void draftAndAboveAllowSideEffects(ToolRiskLevel level) {
        assertThat(level.allowsSideEffects()).isTrue();
    }

    @Test
    void readOnlyDoesNotAllowSideEffects() {
        assertThat(ToolRiskLevel.READ_ONLY.allowsSideEffects()).isFalse();
    }

    @Test
    void shouldHaveFourRiskLevels() {
        assertThat(ToolRiskLevel.values()).hasSize(4);
    }
}
