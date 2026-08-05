package com.minialalipay.account.application.reconciliation;

import com.minialalipay.account.domain.reconciliation.ReconciliationDiffRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReconciliationDiffApplicationServiceTest {
    @Test
    void 只追加差异证据而不修改资金事实() {
        ReconciliationDiffRepository repository = mock(ReconciliationDiffRepository.class);
        ReconciliationDiffApplicationService service = new ReconciliationDiffApplicationService(repository);
        Instant detectedAt = Instant.parse("2026-08-04T08:00:00Z");

        service.record("01K1DIFF02GH3JK4MN5PQRSTV", "01K1TX0002GH3JK4MN5PQRSTV",
                "SUCCESS_FACT_MISMATCH", "{\"successConsistent\":true}",
                "{\"successConsistent\":false}", "01K1CASE02GH3JK4MN5PQRSTV",
                "0123456789abcdef0123456789abcdef", detectedAt);

        verify(repository).append("01K1DIFF02GH3JK4MN5PQRSTV", "01K1TX0002GH3JK4MN5PQRSTV",
                "SUCCESS_FACT_MISMATCH", "{\"successConsistent\":true}",
                "{\"successConsistent\":false}", "01K1CASE02GH3JK4MN5PQRSTV",
                "0123456789abcdef0123456789abcdef", detectedAt);
    }
}
