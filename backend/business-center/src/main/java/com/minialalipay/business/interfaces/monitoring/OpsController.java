package com.minialalipay.business.interfaces.monitoring;

import com.minialalipay.business.application.monitoring.MonitoringApplicationService;
import com.minialalipay.business.application.monitoring.DailyReportJob;
import com.minialalipay.business.application.monitoring.OpsTransactionQueryService;
import com.minialalipay.business.application.monitoring.DashboardSummary;
import com.minialalipay.business.application.monitoring.DashboardSummaryApplicationService;
import com.minialalipay.business.application.port.OpsTransactionQueryPort;
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
import org.springframework.lang.Nullable;
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
    private final DashboardSummaryApplicationService dashboardService;
    private final DailyReportJob dailyReportJob;
    private final OpsAccessGuard access;
    private final RequestIdGenerator requestIds;
    private final IdempotencyKeyValidator idempotencyKeyValidator;

    /** 创建监控运维 Controller。 */
    public OpsController(MonitoringApplicationService service, OpsAccessGuard access,
                         RequestIdGenerator requestIds, IdempotencyKeyValidator idempotencyKeyValidator) {
        this(service, null, null, null, access, requestIds, idempotencyKeyValidator);
    }

    /** 创建监控运维 Controller（含运营交易查询）。 */
    public OpsController(MonitoringApplicationService service, OpsTransactionQueryService transactionService,
                         OpsAccessGuard access, RequestIdGenerator requestIds, IdempotencyKeyValidator idempotencyKeyValidator) {
        this(service, transactionService, null, null, access, requestIds, idempotencyKeyValidator);
    }

    /** 创建监控运维 Controller，包含看板汇总与全局交易只读查询。 */
    public OpsController(MonitoringApplicationService service, OpsTransactionQueryService transactionService,
                         DashboardSummaryApplicationService dashboardService, OpsAccessGuard access,
                         RequestIdGenerator requestIds, IdempotencyKeyValidator idempotencyKeyValidator) {
        this(service, transactionService, dashboardService, null, access, requestIds, idempotencyKeyValidator);
    }

    /** 创建监控运维 Controller，包含受控临时报表预览。 */
    @org.springframework.beans.factory.annotation.Autowired
    public OpsController(MonitoringApplicationService service, OpsTransactionQueryService transactionService,
                         DashboardSummaryApplicationService dashboardService, @Nullable DailyReportJob dailyReportJob,
                         OpsAccessGuard access, RequestIdGenerator requestIds, IdempotencyKeyValidator idempotencyKeyValidator) {
        this.service = service;
        this.transactionService = transactionService;
        this.dashboardService = dashboardService;
        this.dailyReportJob = dailyReportJob;
        this.access = access;
        this.requestIds = requestIds;
        this.idempotencyKeyValidator = idempotencyKeyValidator;
    }

    /** 查询可信运行看板汇总；仅运营、管理员和观察者可读取，结果不包含资金敏感材料。 */
    @GetMapping("/dashboard-summary")
    public ResponseEntity<ApiResponse<DashboardSummary>> dashboardSummary(
            @RequestHeader("X-User-Roles") String roles, HttpServletRequest request) {
        access.requireRead(roles);
        return ResponseEntity.ok(success(dashboardService.getSummary(), request));
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

    /** 查询脱敏告警分页；支持按状态与级别（severity）筛选。 */
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<AlertPage>> alerts(
            @RequestHeader("X-User-Roles") String roles,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            HttpServletRequest request) {
        access.requireRead(roles);
        List<AlertResponse> items = service.listAlerts(status, severity, cursor, limit)
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

    /** 按指定已结束业务日重新生成正式日报；失败时只返回门禁结果，不发布指标。 */
    @PostMapping("/daily-reports/{reportDate}/generate")
    public ResponseEntity<ApiResponse<DailyReportGenerationResponse>> generateDailyReport(
            @RequestHeader("X-User-Roles") String roles,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            HttpServletRequest request) {
        access.requireAdmin(roles);
        DailyReportJob.DailyReportResult result = dailyReportJob.run(reportDate);
        return ResponseEntity.ok(success(DailyReportGenerationResponse.from(result), request));
    }

    /**
     * 生成昨日零点至当前时刻的临时报表预览；仅管理员可执行，且不会发布或覆盖正式 T+1 报表。
     *
     * <p>时间窗由服务端时钟按上海业务日计算，避免客户端时间篡改。质量门禁失败时只返回检查证据，
     * 指标列表为空，调用方不得将其误作正式日报。</p>
     */
    @PostMapping("/daily-report-previews")
    public ResponseEntity<ApiResponse<DailyReportPreviewResponse>> generateDailyReportPreview(
            @RequestHeader("X-User-Roles") String roles, HttpServletRequest request) {
        access.requireAdmin(roles);
        DailyReportJob.DailyReportPreview preview = dailyReportJob.previewFromPreviousBusinessDay();
        return ResponseEntity.ok(success(DailyReportPreviewResponse.from(preview), request));
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
            @RequestParam(required = false) String initiator,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            HttpServletRequest request) {
        access.requireRead(roles);
        List<OpsTransactionResponse> items = transactionService
                .listTransactions(status, businessType, normalizeKeyword(initiator), cursor, limit, from, to)
                .stream().map(OpsTransactionResponse::from).toList();
        // 列表按创建时间倒序；下一页游标携带边界行创建时间与交易 ID，交由客户端原样回传。
        String nextCursor = items.size() == limit
                ? OpsTransactionQueryPort.encodeCursor(items.getLast().createdAt(), items.getLast().transactionId())
                : null;
        return ResponseEntity.ok(success(new OpsTransactionPage(items, nextCursor), request));
    }

    /** 查询单笔脱敏交易详情与关联的 TCC 全局、Outbox 事件和活动工单；不暴露完整账户标识。 */
    @GetMapping("/transactions/{id}")
    public ResponseEntity<ApiResponse<OpsTransactionDetailResponse>> transaction(
            @RequestHeader("X-User-Roles") String roles, @PathVariable String id, HttpServletRequest request) {
        access.requireRead(roles);
        return ResponseEntity.ok(success(OpsTransactionDetailResponse.from(transactionService.getTransaction(id)), request));
    }

    /** 查询交易链路片段；按交易归属的链路编号返回跨服务脱敏 Span（业务中心 + 账户账本 + 用户审计 + AI）。 */
    @GetMapping("/transactions/{id}/trace")
    public ResponseEntity<ApiResponse<List<TraceSpanResponse>>> transactionTrace(
            @RequestHeader("X-User-Roles") String roles, @PathVariable String id, HttpServletRequest request) {
        access.requireRead(roles);
        List<TraceSpanResponse> spans = transactionService.getTrace(id).stream().map(TraceSpanResponse::from).toList();
        return ResponseEntity.ok(success(spans, request));
    }

    /** 按链路编号查询跨服务脱敏 Span；链路无已核验片段时返回空列表。 */
    @GetMapping("/traces/{traceId}")
    public ResponseEntity<ApiResponse<List<TraceSpanResponse>>> traceByTraceId(
            @RequestHeader("X-User-Roles") String roles, @PathVariable String traceId, HttpServletRequest request) {
        access.requireRead(roles);
        List<TraceSpanResponse> spans = transactionService.getTraceByTraceId(traceId).stream()
                .map(TraceSpanResponse::from).toList();
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

    /** 发起人关键词归一化：去除首尾空白，空值转 null；超长按无效请求拒绝，避免拖垮 LIKE 查询。 */
    private String normalizeKeyword(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        if (trimmed.length() > 64) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        return trimmed;
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

    /** 临时报表预览响应，仅代表调用时刻的只读快照。 */
    public record DailyReportPreviewResponse(Instant windowStart, Instant windowEnd, String status,
                                             List<DailyReportPreviewMetric> metrics,
                                             List<DailyReportPreviewCheck> qualityChecks,
                                             List<DailyReportFailure> failures) {
        static DailyReportPreviewResponse from(DailyReportJob.DailyReportPreview preview) {
            return new DailyReportPreviewResponse(preview.windowStart(), preview.windowEnd(),
                    preview.publishable() ? "READY" : "BLOCKED",
                    preview.metrics().stream().map(metric -> new DailyReportPreviewMetric(metric.metricCode(), metric.value(),
                            "v" + metric.version())).toList(),
                    preview.checks().stream().map(check -> new DailyReportPreviewCheck(check.ruleCode(), check.status(),
                            check.checkedCount(), check.failedCount())).toList(),
                    preview.failures().stream().map(DailyReportFailure::from).toList());
        }
    }

    /** 临时报表预览中的单个指标。 */
    public record DailyReportPreviewMetric(String metricCode, long value, String metricVersion) { }

    /** 临时报表预览中的质量门禁结果。 */
    public record DailyReportPreviewCheck(String ruleCode, String status, long checkedCount, long failedCount) { }

    /** 正式日报生成响应，质量门禁阻断时 metrics 必须为空。 */
    public record DailyReportGenerationResponse(LocalDate reportDate, String status, Instant generatedAt,
                                                List<DailyReportPreviewMetric> metrics,
                                                List<DailyReportPreviewCheck> qualityChecks,
                                                List<DailyReportFailure> failures) {
        static DailyReportGenerationResponse from(DailyReportJob.DailyReportResult result) {
            return new DailyReportGenerationResponse(result.reportDate(), result.status(), result.generatedAt(),
                    result.metrics().stream().map(metric -> new DailyReportPreviewMetric(metric.metricCode(), metric.value(),
                            "v" + metric.version())).toList(),
                    result.checks().stream().map(check -> new DailyReportPreviewCheck(check.ruleCode(), check.status(),
                            check.checkedCount(), check.failedCount())).toList(),
                    result.failures().stream().map(DailyReportFailure::from).toList());
        }
    }

    /** 阻断报告中的脱敏失败事件摘要。 */
    public record DailyReportFailure(String eventId, String reason, int retryCount, String status) {
        static DailyReportFailure from(com.minialalipay.business.application.port.DailyReportStore.InboxFailure failure) {
            return new DailyReportFailure(failure.eventId(), failure.reason(), failure.retryCount(), failure.status());
        }
    }

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

    /** 链路追溯片段响应 DTO；transactionId 可为空（非交易归属的服务片段）。 */
    public record TraceSpanResponse(String service, String operation, String status, String detail, String traceId,
                                    Instant occurredAt, String transactionId) {
        static TraceSpanResponse from(TraceSpan span) {
            return new TraceSpanResponse(span.service(), span.operation(), span.status(), span.detail(),
                    span.traceId(), span.occurredAt(), span.transactionId());
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
