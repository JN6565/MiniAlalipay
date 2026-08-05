package com.minialalipay.account.interfaces.reconciliation;

import com.minialalipay.account.application.reconciliation.ReconciliationDiffApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** 供业务中心终态发布器追加对账差异的内部接口。 */
@RestController
@RequestMapping("/internal/v1/reconciliation-diffs")
public class ReconciliationDiffController {
    private final ReconciliationDiffApplicationService service;

    public ReconciliationDiffController(ReconciliationDiffApplicationService service) {
        this.service = service;
    }

    /**
     * 幂等记录差异并返回空响应；调用方必须同时创建人工工单，且不得据此直接改账。
     */
    @PostMapping
    public ResponseEntity<Void> record(@Valid @RequestBody ReconciliationDiffRequest request) {
        service.record(request.diffId(), request.transactionId(), request.diffType(), request.expectedJson(),
                request.actualJson(), request.manualCaseId(), request.traceId(), request.detectedAt());
        return ResponseEntity.noContent().build();
    }

    /** 终态核验差异请求，JSON 字段只承载脱敏布尔事实。 */
    public record ReconciliationDiffRequest(
            @NotBlank String diffId,
            @NotBlank String transactionId,
            @NotBlank String diffType,
            @NotBlank String expectedJson,
            @NotBlank String actualJson,
            @NotBlank String manualCaseId,
            @NotBlank String traceId,
            @NotNull Instant detectedAt) { }
}
