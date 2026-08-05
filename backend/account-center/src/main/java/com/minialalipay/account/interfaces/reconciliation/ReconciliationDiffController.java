package com.minialalipay.account.interfaces.reconciliation;

import com.minialalipay.account.application.reconciliation.ReconciliationDiffApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
     * 幂等记录差异并返回空响应。
     *
     * <p>仅限业务中心终态发布器调用；应用服务在 ledger_db 本地事务中按 diffId 追加脱敏证据。
     * 调用方必须同时创建人工工单，且不得据此直接改账。参数错误返回 400，同一 diffId 绑定不同证据
     * 返回 409，持久化失败返回统一内部错误。</p>
     */
    @PostMapping
    public ResponseEntity<Void> record(@Valid @RequestBody ReconciliationDiffRequest request) {
        service.record(request.diffId(), request.transactionId(), request.diffType(), request.expectedJson(),
                request.actualJson(), request.manualCaseId(), request.traceId(), request.detectedAt());
        return ResponseEntity.noContent().build();
    }

    /** 终态核验差异请求，JSON 字段只承载脱敏布尔事实。 */
    public record ReconciliationDiffRequest(
            @NotBlank @Size(min = 26, max = 26) @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String diffId,
            @NotBlank @Size(min = 26, max = 26) @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String transactionId,
            @NotBlank @Size(max = 64) String diffType,
            @NotBlank @Size(min = 2, max = 4096) String expectedJson,
            @NotBlank @Size(min = 2, max = 4096) String actualJson,
            @NotBlank @Size(min = 26, max = 26) @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{26}$") String manualCaseId,
            @NotBlank @Pattern(regexp = "^[a-fA-F0-9]{32}$") String traceId,
            @NotNull Instant detectedAt) { }
}
