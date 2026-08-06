package com.minialalipay.ai.domain.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP 工具目录测试：验证 13 个 P0 工具全部注册且 Schema 完整。
 */
class ToolCatalogTest {

    private final ToolCatalog catalog = new ToolCatalog();

    @Test
    void shouldRegisterAll13P0Tools() {
        assertThat(catalog.allTools()).hasSize(13);
    }

    @Test
    void shouldInclude7ReadOnlyTools() {
        long count = catalog.allTools().values().stream()
                .filter(def -> def.riskLevel() == ToolRiskLevel.READ_ONLY)
                .count();
        assertThat(count).isEqualTo(7);
    }

    @Test
    void shouldInclude2DraftTools() {
        long count = catalog.allTools().values().stream()
                .filter(def -> def.riskLevel() == ToolRiskLevel.DRAFT)
                .count();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldInclude2ValidationTools() {
        long count = catalog.allTools().values().stream()
                .filter(def -> def.riskLevel() == ToolRiskLevel.VALIDATION)
                .count();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldInclude2HighRiskWriteTools() {
        long count = catalog.allTools().values().stream()
                .filter(def -> def.riskLevel() == ToolRiskLevel.HIGH_RISK_WRITE)
                .count();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void allToolsShouldHaveVersionV1() {
        catalog.allTools().values().forEach(def ->
                assertThat(def.version()).isEqualTo("v1"));
    }

    @Test
    void allToolsShouldRequireLogin() {
        catalog.allTools().values().forEach(def ->
                assertThat(def.requiresLogin()).isTrue());
    }

    @Test
    void allToolsShouldHaveTimeout() {
        catalog.allTools().values().forEach(def ->
                assertThat(def.timeoutSeconds()).isGreaterThan(0));
    }

    @Test
    void allToolsShouldHaveInputSchema() {
        catalog.allTools().values().forEach(def -> {
            assertThat(def.inputSchema()).isNotNull();
            assertThat(def.inputSchema()).containsKey("type");
        });
    }

    @Test
    void allToolsShouldHaveOutputSchema() {
        catalog.allTools().values().forEach(def -> {
            assertThat(def.outputSchema()).isNotNull();
            assertThat(def.outputSchema()).containsKey("type");
        });
    }

    @Test
    void highRiskToolsShouldNotExposeConfirmationHandleInSchema() {
        catalog.allTools().values().stream()
                .filter(def -> def.riskLevel() == ToolRiskLevel.HIGH_RISK_WRITE)
                .forEach(def -> {
                    String schema = def.inputSchema().toString();
                    assertThat(schema).doesNotContain("confirmationHandle");
                    assertThat(schema).doesNotContain("confirmationToken");
                    assertThat(schema).doesNotContain("paymentPassword");
                });
    }

    @Test
    void shouldHaveAllRequiredTools() {
        assertThat(catalog.lookup("search_payees")).isPresent();
        assertThat(catalog.lookup("get_balance")).isPresent();
        assertThat(catalog.lookup("get_account_summary")).isPresent();
        assertThat(catalog.lookup("list_transactions")).isPresent();
        assertThat(catalog.lookup("get_transaction_status")).isPresent();
        assertThat(catalog.lookup("get_credit_summary")).isPresent();
        assertThat(catalog.lookup("list_credit_bills")).isPresent();
        assertThat(catalog.lookup("create_transfer_draft")).isPresent();
        assertThat(catalog.lookup("create_credit_repayment_draft")).isPresent();
        assertThat(catalog.lookup("validate_transfer_draft")).isPresent();
        assertThat(catalog.lookup("prepare_confirmation_card")).isPresent();
        assertThat(catalog.lookup("submit_confirmed_transfer")).isPresent();
        assertThat(catalog.lookup("submit_confirmed_credit_repayment")).isPresent();
    }

    @Test
    void shouldReturnEmptyForUnknownTool() {
        assertThat(catalog.lookup("nonexistent_tool")).isEmpty();
    }
}
