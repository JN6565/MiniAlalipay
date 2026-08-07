package com.minialalipay.business.interfaces.manualcase;

import com.minialalipay.business.application.manualcase.ManualCaseApplicationService;
import com.minialalipay.business.domain.manualcase.ManualCase;
import com.minialalipay.business.domain.manualcase.ManualCaseStatus;
import com.minialalipay.business.domain.manualcase.ManualCaseType;
import com.minialalipay.business.interfaces.security.OpsAccessGuard;
import com.minialalipay.common.api.ApiResponse;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * B 端人工工单查询与处置 API。
 *
 * <p>角色只从网关注入的 {@code X-User-Roles} 读取。管理员与运营人员均可查询和处置工单，
 * 并记录操作者、理由、证据和请求编号。接口不直接修改交易或资金状态。</p>
 */
@RestController
@RequestMapping("/api/v1/manual-cases")
public class ManualCaseController {
    private final ManualCaseApplicationService service;
    private final OpsAccessGuard access;
    private final RequestIdGenerator requestIds;
    private final IdempotencyKeyValidator idempotencyKeyValidator;

    /** 创建工单 Controller。 */
    public ManualCaseController(ManualCaseApplicationService service, OpsAccessGuard access,
                                RequestIdGenerator requestIds, IdempotencyKeyValidator idempotencyKeyValidator) {
        this.service = service;
        this.access = access;
        this.requestIds = requestIds;
        this.idempotencyKeyValidator = idempotencyKeyValidator;
    }

    /** 查询运营可见工单；支持按状态、类型过滤，过滤条件为空表示不限定。 */
    @GetMapping
    public ResponseEntity<ApiResponse<ManualCasePage>> list(
            @RequestHeader("X-User-Roles") String roles,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        access.requireRead(roles);
        ManualCaseStatus statusEnum = parseStatus(status);
        ManualCaseType typeEnum = parseType(type);
        List<ManualCaseResponse> items = service.list(cursor, statusEnum, typeEnum, limit)
                .stream().map(ManualCaseResponse::from).toList();
        String nextCursor = items.size() == limit ? items.getLast().caseId() : null;
        return ResponseEntity.ok(success(new ManualCasePage(items, nextCursor), request));
    }

    private ManualCaseStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ManualCaseStatus.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private ManualCaseType parseType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return ManualCaseType.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    /** 按 CAS 版本处置工单；幂等由网关和审计存储的写请求边界保证。 */
    @PostMapping("/{id}/decisions")
    public ResponseEntity<ApiResponse<ManualCaseResponse>> decide(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader("X-User-Roles") String roles,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String id,
            @Valid @RequestBody DecideManualCaseRequest body,
            HttpServletRequest request) {
        access.requireWrite(roles);
        if (!idempotencyKeyValidator.isValid(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        ManualCase value = service.decide(operatorId, id, body.decision(), body.version(), body.reason(), body.evidence(),
                idempotencyKey);
        return ResponseEntity.ok(success(ManualCaseResponse.from(value), request));
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestIds.resolve(request.getHeader("X-Request-Id")), request.getHeader("X-Trace-Id"));
    }

    /** 工单处置请求；领取不要求理由和证据，其他动作由领域状态机校验。 */
    public record DecideManualCaseRequest(@NotNull ManualCaseApplicationService.Decision decision,
                                          @Min(0) long version, @Size(max = 500) String reason,
                                          @Size(max = 2000) String evidence) { }
    /**
     * 运营侧脱敏工单 DTO。
     *
     * <p>除工单标识与状态外，暴露处置审计事实：触发原因码（规则命中）、操作者、处置理由、证据引用与处置时间，
     * 供运营在人工确认台核对 PRD 要求的规则命中、操作者和处置上下文，不暴露任何资金或敏感原值。</p>
     */
    public record ManualCaseResponse(String caseId, String caseType, String subjectType, String subjectId,
                                     String status, String reasonCode, String operatorId, String lastReason,
                                     String evidenceReference, long version, Instant createdAt, Instant updatedAt) {
        static ManualCaseResponse from(ManualCase value) {
            return new ManualCaseResponse(value.getCaseId(), value.getType().name(), value.getSubjectType(),
                    value.getSubjectId(), value.getStatus().name(), value.getReasonCode(), value.getOperatorId(),
                    value.getLastReason(), value.getEvidenceReference(), value.getVersion(), value.getCreatedAt(),
                    value.getUpdatedAt());
        }
    }
    /** 基于稳定 ID 游标的工单分页响应。 */
    public record ManualCasePage(List<ManualCaseResponse> items, String nextCursor) { }
}
