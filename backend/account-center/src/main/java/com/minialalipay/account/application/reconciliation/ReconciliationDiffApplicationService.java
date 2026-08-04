package com.minialalipay.account.application.reconciliation;

import com.minialalipay.account.domain.reconciliation.ReconciliationDiffRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 接收终态发布器发现的差异，并在 ledger_db 中幂等追加证据。 */
@Service
public class ReconciliationDiffApplicationService {
    private final ReconciliationDiffRepository repository;

    public ReconciliationDiffApplicationService(ReconciliationDiffRepository repository) {
        this.repository = repository;
    }

    /**
     * 追加差异证据；事务只覆盖 ledger_db 写入，不直接修正任何资金事实。
     */
    @Transactional
    public void record(String diffId, String transactionId, String diffType, String expectedJson,
                       String actualJson, String manualCaseId, String traceId, Instant detectedAt) {
        repository.append(diffId, transactionId, diffType, expectedJson, actualJson,
                manualCaseId, traceId, detectedAt);
    }
}
