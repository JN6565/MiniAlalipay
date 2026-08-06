package com.minialalipay.business.interfaces.monitoring;

import com.minialalipay.business.application.monitoring.MonitoringApplicationService;
import com.minialalipay.business.application.monitoring.OpsTransactionQueryService;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionDetail;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionRow;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.TraceSpan;
import com.minialalipay.business.domain.monitoring.Alert;
import com.minialalipay.business.domain.monitoring.AlertRule;
import com.minialalipay.business.domain.monitoring.DataQualityResult;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import com.minialalipay.business.domain.monitoring.MetricDefinition;
import com.minialalipay.business.domain.monitoring.RealtimeMetric;
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
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;

/**
 * B 端监控运维 API。
 *
 * <p>实时指标、日报、数据质量、指标口径和告警投影只允许运营读取；告警处置（确认/恢复/关闭）需要管理员或
 * 运营人员并记录操作者、理由、证据和请求编号。接口只操作运营投影，不修改交易或资金状态。</p>
 */
@RestController
@RequestMapping("/api/v1/ops")
public class OpsController {
    private final MonitoringApplicationService service;
    private final OpsTransactionQueryService transactionService;
    private final OpsAccessGuard access;
    private final RequestIdGenerator requestIds;
    private final IdempotencyKeyValidator idempotencyKeyValidator;

    /** 创建监控运维 Controller。 */
    public OpsController(MonitoringApplicationService service, OpsAccessGuard access,
                         RequestIdGenerator requestIds, IdempotencyKeyValidator idempotencyKeyValidator) {
        this(service, null, access, requestIds, idempotencyKeyValidator);
    }

    /** 创建监控运维 Controller（含运营交易查询）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public OpsController(MonitoringApplicationService service, OpsTransactionQueryService transactionService,
                         OpsAccessGuard access, RequestIdGenerator requestIds, IdempotencyKeyValidator idempotencyKeyValidator) {
        this.service = service;
        this.transactionService = transactionService;
        this.access = access;
        this.requestIds = requestIds;
        this.idempotencyKeyValidator = idempotencyKeyValidator;
    }

    /** 查询分钟级脱敏实时指标。 */
    @GetMapping("/realtime-metrics")
    public ResponseEntity<ApiResponse<List<RealtimeMetric>>> realtimeMetrics(
            @RequestHeader("X-User-Roles") String roles,
            @RequestParam(required = false) String metricCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletRequest request) {
        access.requireRead(roles);
        return ResponseEntity.ok(success(service.listRealtimeMetrics(metricCode, from, to), request));
    }

    /** 查询脱敏告警分页。 */
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<AlertPage>> alerts(
            @RequestHeader("X-User-Roles") String roles,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        access.requireRead(roles);
        List<AlertResponse> items = service.listAlerts(status, cursor, limit)
                .stream().map(AlertResponse::from).toList();
        String nextCursor = items.size() == limit ? items.getLast().alertId() : null;
        return ResponseEntity.ok(success(new AlertPage(items, nextCursor), request));
    }

    /** 确认开放告警。 */
    @PostMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<ApiResponse<AlertResponse>> acknowledge(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader("X-User-Roles") String roles,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String id,
            @Valid @RequestBody AcknowledgeAlertRequest body,
            HttpServletRequest request) {
        access.requireWrite(roles);
        if (!idempotencyKeyValidator.isValid(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        Alert value = service.acknowledgeAlert(operatorId, id, body.version(), body.reason(), idempotencyKey);
        return ResponseEntity.ok(success(AlertResponse.from(value), request));
    }

    /** 标记已确认告警已解决。 */
    @PostMapping("/alerts/{id}/resolve")
    public ResponseEntity<ApiResponse<AlertResponse>> resolve(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader("X-User-Roles") String roles,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String id,
            @Valid @RequestBody ResolveAlertRequest body,
            HttpServletRequest request) {
        access.requireWrite(roles);
        if (!idempotencyKeyValidator.isValid(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        Alert value = service.resolveAlert(operatorId, id, body.version(), body.reason(), body.evidence(), idempotencyKey);
        return ResponseEntity.ok(success(AlertResponse.from(value), request));
    }

    /** 关闭已恢复的告警。 */
    @PostMapping("/alerts/{id}/close")
    public ResponseEntity<ApiResponse<AlertResponse>> close(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader("X-User-Roles") String roles,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable String id,
            @Valid @RequestBody CloseAlertRequest body,
            HttpServletRequest request) {
        access.requireWrite(roles);
        if (!idempotencyKeyValidator.isValid(idempotencyKey)) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
        Alert value = service.closeAlert(operatorId, id, body.version(), body.reason(), body.evidence(), idempotencyKey);
        return ResponseEntity.ok(success(AlertResponse.from(value), request));
    }

    /** 查询通过质量门禁的 T+1 报表。 */
    @GetMapping("/daily-reports")
    public ResponseEntity<ApiResponse<List<DailyMetric>>> dailyReports(
            @RequestHeader("X-User-Roles") String roles,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            HttpServletRequest request) {
        access.requireRead(roles);
        return ResponseEntity.ok(success(service.listDailyReports(reportDate), request));
    }

    /** 查询数据质量结果。 */
    @GetMapping("/data-quality")
    public ResponseEntity<ApiResponse<List<DataQualityResult>>> dataQuality(
            @RequestHeader("X-User-Roles") String roles,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDate,
            @RequestParam(required = false) String jobCode,
            @RequestParam(required = false) String ruleCode,
            HttpServletRequest request) {
        access.requireRead(roles);
        return ResponseEntity.ok(success(service.listDataQuality(dataDate, jobCode, ruleCode), request));
    }

    /** 查询历史指标口径版本。 */
    @GetMapping("/metric-definitions")
    public ResponseEntity<ApiResponse<List<MetricDefinition>>> metricDefinitions(
            @RequestHeader("X-User-Roles") String roles,
            HttpServletRequest request) {
        access.requireRead(roles);
        return ResponseEntity.ok(success(service.listMetricDefinitions(), request));
    }

    /** 按游标分页查询全平台脱敏交易摘要；管理员与运营人员只读，金额为整数分。 */
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<OpsTransactionPage>> transactions(
            @RequestHeader("X-User-Roles") String roles,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletRequest request) {
        access.requireRead(roles);
        List<OpsTransactionResponse> items = transactionService.listTransactions(status, businessType, cursor, limit, from, to)
                .stream().map(OpsTransactionResponse::from).toList();
        String nextCursor = items.size() == limit ? items.getLast().transactionId() : null;
        return ResponseEntity.ok(success(new OpsTransactionPage(items, nextCursor), request));
    }

    /** 查询单笔脱敏交易详情与关联的 TCC 全局、Outbox 事件和活动工单；不暴露完整账户标识。 */
    @GetMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<OpsTransactionDetailResponse>> transaction(
            @RequestHeader("X-User-Roles") String roles, @PathVariable String id, HttpServletRequest request) {
        access.requireRead(roles);
        return ResponseEntity.ok(success(OpsTransactionDetailResponse.from(transactionService.getTransaction(id)), request));
    }

    /** 查询交易链路片段；仅返回业务中心可核验的资金事实阶段，完整跨服务 Trace 归阶段七 OTel 集成。 */
    @GetMapping("/transactions/{id}/trace")
    public ResponseEntity<ApiResponse<List<TraceSpanResponse>>> transactionTrace(
            @RequestHeader("X-User-Roles") String roles, @PathVariable String id, HttpServletRequest request) {
        access.requireRead(roles);
        List<TraceSpanResponse> spans = transactionService.getTrace(id).stream().map(TraceSpanResponse::from).toList();
        return ResponseEntity.ok(success(spans, request));
    }

    /** 查询全部告警规则及阈值配置；管理员与运营人员只读。 */
    @GetMapping("/alert-rules")
    public ResponseEntity<ApiResponse<List<AlertRuleResponse>>> alertRules(
            @RequestHeader("X-User-Roles") String roles, HttpServletRequest request) {
        access.requireRead(roles);
        List<AlertRuleResponse> items = service.listAlertRules().stream().map(AlertRuleResponse::from).toList();
        return ResponseEntity.ok(success(items, request));
    }

    /** 按版本 CAS 更新告警规则阈值；仅管理员可配置并记录操作者，重复设置同一阈值无副作用。 */
    @PostMapping("/alert-rules/{ruleCode}/thresholds")
    public ResponseEntity<ApiResponse<AlertRuleResponse>> updateAlertRuleThreshold(
            @RequestHeader("X-User-Id") String operatorId,
            @RequestHeader("X-User-Roles") String roles,
            @PathVariable String ruleCode,
            @Valid @RequestBody UpdateAlertRuleThresholdRequest body,
            HttpServletRequest request) {
        access.requireAdmin(roles);
        AlertRule value = service.updateAlertRuleThreshold(operatorId, ruleCode, body.thresholdValue(), body.version());
        return ResponseEntity.ok(success(AlertRuleResponse.from(value), request));
    }

    private <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, requestIds.resolve(request.getHeader("X-Request-Id")),
                request.getHeader("X-Trace-Id"));
    }

    /** 确认告警请求；版本 CAS 与处置理由必填。 */
    public record AcknowledgeAlertRequest(@NotNull @Min(0) Long version,
                                          @NotBlank @Size(max = 500) String reason) { }
    /** 恢复告警请求；必须附带处置证据。 */
    public record ResolveAlertRequest(@NotNull @Min(0) Long version,
                                      @NotBlank @Size(max = 500) String reason,
                                      @NotBlank @Size(max = 2000) String evidence) { }
    /** 关闭告警请求；必须附带最终证据。 */
    public record CloseAlertRequest(@NotNull @Min(0) Long version,
                                    @NotBlank @Size(max = 500) String reason,
                                    @NotBlank @Size(max = 2000) String evidence) { }
    /** 运营侧脱敏告警 DTO。 */
    public record AlertResponse(String alertId, String alertType, String severity, String status,
                                long version, Instant createdAt, Instant updatedAt) {
        static AlertResponse from(Alert value) {
            return new AlertResponse(value.getAlertId(), value.getAlertType(), value.getSeverity(),
                    value.getStatus().name(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt());
        }
    }
    /** 基于稳定 ID 游标的告警分页响应。 */
    public record AlertPage(List<AlertResponse> items, String nextCursor) { }

    /** 运营侧脱敏交易摘要 DTO，不暴露完整用户或账户标识。 */
    public record OpsTransactionResponse(String transactionId, String businessType, String sourceType,
                                         String sourceOrderId, String initiatorMasked, long amountFen, String status,
                                         String riskLevel, String traceId, Instant createdAt, Instant updatedAt) {
        static OpsTransactionResponse from(OpsTransactionRow row) {
            return new OpsTransactionResponse(row.transactionId(), row.businessType(), row.sourceType(),
                    row.sourceOrderId(), row.initiatorMasked(), row.amountFen(), row.status(), row.riskLevel(),
                    row.traceId(), row.createdAt(), row.updatedAt());
        }
    }

    /** 运营侧单笔交易详情 DTO。 */
    public record OpsTransactionDetailResponse(OpsTransactionResponse transaction, String fundingSource,
                                               String tccStatus, int tccRetryCount, String latestOutboxEventType,
                                               String outboxStatus, String activeManualCaseId) {
        static OpsTransactionDetailResponse from(OpsTransactionDetail detail) {
            return new OpsTransactionDetailResponse(OpsTransactionResponse.from(detail.row()), detail.fundingSource(),
                    detail.tccStatus(), detail.tccRetryCount(), detail.latestOutboxEventType(), detail.outboxStatus(),
                    detail.activeManualCaseId());
        }
    }

    /** 交易分页响应。 */
    public record OpsTransactionPage(List<OpsTransactionResponse> items, String nextCursor) { }

    /** 链路追溯片段响应 DTO。 */
    public record TraceSpanResponse(String service, String operation, String status, String detail, String traceId,
                                    Instant occurredAt) {
        static TraceSpanResponse from(TraceSpan span) {
            return new TraceSpanResponse(span.service(), span.operation(), span.status(), span.detail(),
                    span.traceId(), span.occurredAt());
        }
    }

    /** 告警规则及阈值配置响应 DTO。 */
    public record AlertRuleResponse(String ruleCode, String ruleName, String metricCode, String severity,
                                    String operator, long thresholdValue, boolean enabled, long version,
                                    String updatedBy, Instant updatedAt) {
        static AlertRuleResponse from(AlertRule rule) {
            return new AlertRuleResponse(rule.ruleCode(), rule.ruleName(), rule.metricCode(), rule.severity(),
                    rule.operator(), rule.thresholdValue(), rule.enabled(), rule.version(), rule.updatedBy(),
                    rule.updatedAt());
        }
    }

    /** 告警规则阈值更新请求；阈值必须为非负整数，规则版本为读取时的 CAS 版本。 */
    public record UpdateAlertRuleThresholdRequest(@NotNull @Min(0) Long thresholdValue,
                                                  @NotNull @Min(0) Long version) { }
}
