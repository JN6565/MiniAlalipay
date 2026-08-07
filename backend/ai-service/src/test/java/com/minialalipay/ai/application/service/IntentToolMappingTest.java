package com.minialalipay.ai.application.service;

import com.minialalipay.ai.application.service.IntentToolMapping.ParamSource;
import com.minialalipay.ai.application.service.IntentToolMapping.ToolMapping;
import com.minialalipay.ai.domain.agent.IntentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 意图→工具映射表测试：验证 8 类已知意图的映射及 UNKNOWN 兜底行为。
 */
class IntentToolMappingTest {

    @Test
    @DisplayName("BALANCE_QUERY 映射到 get_balance，1 个工具且非链式依赖")
    void shouldMapBalanceQueryToGetBalance() {
        List<ToolMapping> mappings = IntentToolMapping.getToolsForIntent(IntentType.BALANCE_QUERY);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0).toolName()).isEqualTo("get_balance");
        assertThat(mappings.get(0).isChainDependent()).isFalse();
    }

    @Test
    @DisplayName("TRANSFER 映射到 3 个链式工具，后两个依赖前驱")
    void shouldMapTransferToThreeChainedTools() {
        List<ToolMapping> mappings = IntentToolMapping.getToolsForIntent(IntentType.TRANSFER);
        assertThat(mappings).hasSize(3);
        assertThat(mappings.get(0).toolName()).isEqualTo("create_transfer_draft");
        assertThat(mappings.get(1).toolName()).isEqualTo("validate_transfer_draft");
        assertThat(mappings.get(2).toolName()).isEqualTo("prepare_confirmation_card");
        assertThat(mappings.get(0).isChainDependent()).isFalse();
        assertThat(mappings.get(1).isChainDependent()).isTrue();
        assertThat(mappings.get(2).isChainDependent()).isTrue();
    }

    @Test
    @DisplayName("TRANSFER 首个工具的参数取自已识别槽位")
    void transferCreateDraftParamsFromSlots() {
        ToolMapping createDraft = IntentToolMapping.getToolsForIntent(IntentType.TRANSFER).get(0);
        assertThat(createDraft.paramSources()).containsEntry("payeeId", ParamSource.SLOTS);
        assertThat(createDraft.paramSources()).containsEntry("amountFen", ParamSource.SLOTS);
        assertThat(createDraft.paramSources()).containsEntry("remark", ParamSource.SLOTS_OPTIONAL);
    }

    @Test
    @DisplayName("CREDIT_SUMMARY 映射到 get_credit_summary")
    void shouldMapCreditSummaryToGetCreditSummary() {
        List<ToolMapping> mappings = IntentToolMapping.getToolsForIntent(IntentType.CREDIT_SUMMARY);
        assertThat(mappings).hasSize(1);
        assertThat(mappings.get(0).toolName()).isEqualTo("get_credit_summary");
    }

    @Test
    @DisplayName("USER_SEARCH 映射到 search_payees，query 参数取自槽位")
    void shouldMapUserSearchToSearchPayees() {
        List<ToolMapping> mappings = IntentToolMapping.getToolsForIntent(IntentType.USER_SEARCH);
        assertThat(mappings).hasSize(1);
        ToolMapping search = mappings.get(0);
        assertThat(search.toolName()).isEqualTo("search_payees");
        assertThat(search.paramSources()).containsEntry("query", ParamSource.SLOTS);
    }

    @Test
    @DisplayName("除 UNKNOWN 外所有已知意图都有非空映射")
    void allNonUnknownIntentsHaveMappings() {
        for (IntentType intent : IntentType.values()) {
            if (intent == IntentType.UNKNOWN) {
                continue;
            }
            assertThat(IntentToolMapping.getToolsForIntent(intent))
                    .as("意图 %s 应有非空映射", intent)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("UNKNOWN 意图返回空列表")
    void unknownIntentReturnsEmptyList() {
        assertThat(IntentToolMapping.getToolsForIntent(IntentType.UNKNOWN)).isEmpty();
    }
}
