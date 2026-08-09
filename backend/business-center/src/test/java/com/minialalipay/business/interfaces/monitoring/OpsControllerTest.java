package com.minialalipay.business.interfaces.monitoring;

import com.minialalipay.business.application.monitoring.MonitoringApplicationService;
import com.minialalipay.business.application.monitoring.OpsTransactionQueryService;
import com.minialalipay.business.application.monitoring.DashboardSummary;
import com.minialalipay.business.application.monitoring.DashboardSummaryApplicationService;
import com.minialalipay.business.application.port.OpsTransactionQueryPort;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionDetail;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionQuery;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionRow;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.TraceSpan;
import com.minialalipay.business.domain.monitoring.Alert;
import com.minialalipay.business.domain.monitoring.AlertRule;
import com.minialalipay.business.domain.monitoring.AlertStatus;
import com.minialalipay.business.domain.monitoring.DataQualityResult;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import com.minialalipay.business.domain.monitoring.MetricDefinition;
import com.minialalipay.business.domain.monitoring.RealtimeMetric;
import com.minialalipay.business.interfaces.error.BusinessCenterExceptionHandler;
import com.minialalipay.business.interfaces.security.OpsAccessGuard;
import com.minialalipay.common.error.CommonExceptionMapper;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import com.minialalipay.common.trace.RequestIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** B 端监控运维 Controller 切片测试：权限门禁、写接口幂等键、响应形状与敏感数据脱敏。 */
class OpsControllerTest {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    private MockMvc mvc;
    private FakeOpsQueryPort opsQueryPort;
    private FakeDashboardService dashboardService;

    @BeforeEach
    void setUp() {
        RequestIdGenerator requestIds = new RequestIdGenerator();
        opsQueryPort = new FakeOpsQueryPort();
        dashboardService = new FakeDashboardService();
        mvc = MockMvcBuilders.standaloneSetup(new OpsController(new FakeService(), new OpsTransactionQueryService(opsQueryPort), dashboardService,
                        new OpsAccessGuard(), requestIds, new IdempotencyKeyValidator()))
                .setControllerAdvice(new BusinessCenterExceptionHandler(new CommonExceptionMapper(), requestIds)).build();
    }

    @Test
    void 运营只读查询返回脱敏投影并回显请求编号() throws Exception {
        mvc.perform(get("/api/v1/ops/realtime-metrics")
                        .header("X-User-Roles", "OPERATOR").header("X-Request-Id", "req-ops-metrics"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value("req-ops-metrics"))
                .andExpect(jsonPath("$.data[0].metricCode").value("transaction.status.changed"))
                .andExpect(jsonPath("$.data[0].qualityStatus").value("PASSED"));

        mvc.perform(get("/api/v1/ops/alerts").header("X-User-Roles", "OPERATOR")
                        .param("status", "OPEN").param("severity", "CRITICAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].alertId").value("alert-1"))
                .andExpect(jsonPath("$.data.items[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.items[0].lastReason").doesNotExist());

        mvc.perform(get("/api/v1/ops/metric-definitions").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("交易成功量"));

        mvc.perform(get("/api/v1/ops/daily-reports").header("X-User-Roles", "ADMIN")
                        .param("reportDate", "2026-08-04"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].metricCode").value("transaction_success"));

        mvc.perform(get("/api/v1/ops/data-quality").header("X-User-Roles", "ADMIN")
                        .param("dataDate", "2026-08-04"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].taskCode").value("交易完整性"))
                .andExpect(jsonPath("$.data[0].ruleCode").value("rule-1"))
                .andExpect(jsonPath("$.data[0].status").value("PASSED"));
    }

    @Test
    void 可信运行看板只向运营角色返回聚合投影() throws Exception {
        mvc.perform(get("/api/v1/ops/dashboard-summary").header("X-User-Roles", "OPERATOR")
                        .header("X-Request-Id", "req-dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-dashboard"))
                .andExpect(jsonPath("$.data.kpis.todayTransactionAmountFen").value(5200))
                .andExpect(jsonPath("$.data.kpis.paymentSuccessRateBps").value(9998))
                .andExpect(jsonPath("$.data.services[0].status").value("UP"))
                .andExpect(jsonPath("$.data.recentTransactions[0].initiatorMasked").value("user01***1234"));

        mvc.perform(get("/api/v1/ops/dashboard-summary").header("X-User-Roles", "USER"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OPS_PERMISSION_REQUIRED"));
    }

    @Test
    void 非运营角色查询与处置均被拒绝() throws Exception {
        mvc.perform(get("/api/v1/ops/alerts").header("X-User-Roles", "USER"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OPS_PERMISSION_REQUIRED"));
        mvc.perform(post("/api/v1/ops/alerts/alert-1/acknowledge")
                        .header("X-User-Roles", "USER").header("X-User-Id", "ops-1")
                        .header("Idempotency-Key", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"开始处置\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OPS_PERMISSION_REQUIRED"));
    }

    @Test
    void 告警处置要求幂等键和严格请求体() throws Exception {
        mvc.perform(post("/api/v1/ops/alerts/alert-1/acknowledge")
                        .header("X-User-Roles", "OPERATOR").header("X-User-Id", "ops-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"开始处置\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));

        mvc.perform(post("/api/v1/ops/alerts/alert-1/resolve")
                        .header("X-User-Roles", "OPERATOR").header("X-User-Id", "ops-1")
                        .header("Idempotency-Key", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"已恢复\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 告警确认与关闭返回CAS后的告警视图() throws Exception {
        mvc.perform(post("/api/v1/ops/alerts/alert-1/acknowledge")
                        .header("X-User-Roles", "OPERATOR").header("X-User-Id", "ops-1")
                        .header("Idempotency-Key", "00000000-0000-0000-0000-000000000001")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"reason\":\"开始处置\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));

        mvc.perform(post("/api/v1/ops/alerts/alert-1/close")
                        .header("X-User-Roles", "ADMIN").header("X-User-Id", "ops-1")
                        .header("Idempotency-Key", "00000000-0000-0000-0000-000000000002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"reason\":\"处置完成\",\"evidence\":\"审计记录xxx\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    void 运营交易查询返回脱敏摘要与详情且拒绝非运营角色() throws Exception {
        mvc.perform(get("/api/v1/ops/transactions").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].transactionId").value("tx-1"))
                .andExpect(jsonPath("$.data.items[0].amountFen").value(5200))
                .andExpect(jsonPath("$.data.items[0].initiatorMasked").value("user01***1234"))
                .andExpect(jsonPath("$.data.items[0].traceId").exists());

        mvc.perform(get("/api/v1/ops/transactions").header("X-User-Roles", "USER"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OPS_PERMISSION_REQUIRED"));

        mvc.perform(get("/api/v1/ops/transactions/tx-1").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.transaction.transactionId").value("tx-1"))
                .andExpect(jsonPath("$.data.tccStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.activeManualCaseId").value("case-1"));

        mvc.perform(get("/api/v1/ops/transactions/tx-1/trace").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].operation").value("统一交易受理"))
                .andExpect(jsonPath("$.data[0].service").value("business-center"))
                .andExpect(jsonPath("$.data[0].transactionId").value("tx-1"))
                .andExpect(jsonPath("$.data[1].service").value("account-center"))
                .andExpect(jsonPath("$.data[2].service").value("ai-service"));
    }

    @Test
    void 交易列表支持按发起人关键词过滤且超长拒绝() throws Exception {
        mvc.perform(get("/api/v1/ops/transactions").header("X-User-Roles", "OPERATOR")
                        .param("initiator", "user01"))
                .andExpect(status().isOk());
        assertThat(opsQueryPort.lastQuery.initiator()).isEqualTo("user01");

        // 发起人关键词去除首尾空白后透传。
        mvc.perform(get("/api/v1/ops/transactions").header("X-User-Roles", "OPERATOR")
                        .param("initiator", "  user01  "))
                .andExpect(status().isOk());
        assertThat(opsQueryPort.lastQuery.initiator()).isEqualTo("user01");

        // 未携带发起人时保持不限定。
        mvc.perform(get("/api/v1/ops/transactions").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isOk());
        assertThat(opsQueryPort.lastQuery.initiator()).isNull();

        // 超长发起人关键词按无效请求拒绝，避免拖垮 LIKE 查询。
        mvc.perform(get("/api/v1/ops/transactions").header("X-User-Roles", "OPERATOR")
                        .param("initiator", "x".repeat(65)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON_INVALID_REQUEST"));
    }

    @Test
    void 按链路编号查询跨服务链路片段() throws Exception {
        mvc.perform(get("/api/v1/ops/traces/abcdef0123456789abcdef0123456789").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].operation").value("统一交易受理"))
                .andExpect(jsonPath("$.data[1].service").value("account-center"));

        // 未知链路编号返回空列表而非 404。
        mvc.perform(get("/api/v1/ops/traces/trace-unknown").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void 运营查询不存在的交易按资源不存在返回() throws Exception {
        mvc.perform(get("/api/v1/ops/transactions/missing").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/ops/transactions/missing/trace").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 告警规则只读查询且仅管理员可调整阈值() throws Exception {
        mvc.perform(get("/api/v1/ops/alert-rules").header("X-User-Roles", "OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ruleCode").value("DUPLICATE_CHARGE"))
                .andExpect(jsonPath("$.data[0].thresholdValue").value(0));

        // 运营人员无权限调整阈值。
        mvc.perform(post("/api/v1/ops/alert-rules/DUPLICATE_CHARGE/thresholds")
                        .header("X-User-Roles", "OPERATOR").header("X-User-Id", "ops-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdValue\":5,\"version\":0}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OPS_PERMISSION_REQUIRED"));

        // 管理员按 CAS 更新阈值，无需幂等键。
        mvc.perform(post("/api/v1/ops/alert-rules/DUPLICATE_CHARGE/thresholds")
                        .header("X-User-Roles", "ADMIN").header("X-User-Id", "admin-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"thresholdValue\":5,\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thresholdValue").value(5))
                .andExpect(jsonPath("$.data.updatedBy").value("admin-1"));
    }

    private static Alert alert() {
        return new Alert("alert-1", "TCC_TIMEOUT", "CRITICAL", AlertStatus.OPEN, null, null, 0L, NOW, NOW);
    }

    /** 覆盖 B 端运营交易查询端口的瘦身替身，仅返回固定脱敏投影；记录最近一次查询条件供断言透传。 */
    private static final class FakeOpsQueryPort implements OpsTransactionQueryPort {
        OpsTransactionQuery lastQuery;
        @Override public List<OpsTransactionRow> listTransactionsForOps(OpsTransactionQuery query) {
            this.lastQuery = query;
            return List.of(row());
        }
        @Override public Optional<OpsTransactionDetail> findTransactionForOps(String transactionId) {
            if (!"tx-1".equals(transactionId)) return Optional.empty();
            return Optional.of(new OpsTransactionDetail(row(), "BALANCE", "SUCCESS", 0,
                    "transaction.status.changed", "COMPLETED", "case-1"));
        }
        @Override public List<TraceSpan> findTraceSpansByTraceId(String traceId) {
            if ("trace-unknown".equals(traceId)) return List.of();
            return List.of(
                    new TraceSpan("business-center", "统一交易受理", "SUCCESS", "TRANSFER/TRANSFER_DRAFT",
                            traceId, NOW, "tx-1"),
                    new TraceSpan("account-center", "账本过账事件", "SUCCESS", "ledger.posted",
                            traceId, NOW.plusSeconds(1), "tx-1"),
                    new TraceSpan("ai-service", "AI 工具调用", "SUCCESS", "balance_query,duration_ms=12",
                            traceId, NOW.plusSeconds(2), null));
        }
        private static OpsTransactionRow row() {
            return new OpsTransactionRow("tx-1", "TRANSFER", "TRANSFER_DRAFT", "order-1", "user01***1234", 5200L,
                    "SUCCESS", "LOW", "abcdef0123456789abcdef0123456789", NOW, NOW.plusSeconds(1));
        }
    }

    /** 覆盖全部公开查询与处置方法的瘦身服务替身，仅返回固定投影。 */
    private static final class FakeService extends MonitoringApplicationService {
        FakeService() {
            super(null, null);
        }

        @Override public List<Alert> listAlerts(String status, String severity, String cursor, int limit) { return List.of(alert()); }
        @Override public Alert acknowledgeAlert(String operatorId, String alertId, long version, String reason, String idempotencyKey) {
            return new Alert(alertId, "TCC_TIMEOUT", "CRITICAL", AlertStatus.ACKNOWLEDGED, operatorId, reason, 1L, NOW, NOW.plusSeconds(1));
        }
        @Override public Alert resolveAlert(String operatorId, String alertId, long version, String reason, String evidence, String idempotencyKey) {
            return new Alert(alertId, "TCC_TIMEOUT", "CRITICAL", AlertStatus.RESOLVED, operatorId, reason, 2L, NOW, NOW.plusSeconds(2));
        }
        @Override public Alert closeAlert(String operatorId, String alertId, long version, String reason, String evidence, String idempotencyKey) {
            return new Alert(alertId, "TCC_TIMEOUT", "CRITICAL", AlertStatus.CLOSED, operatorId, reason, 3L, NOW, NOW.plusSeconds(3));
        }
        @Override public List<RealtimeMetric> listRealtimeMetrics(String metricCode, Instant from, Instant to) {
            return List.of(new RealtimeMetric("transaction.status.changed", NOW, 12L, "v1", "PASSED"));
        }
        @Override public List<DailyMetric> listDailyReports(LocalDate reportDate) {
            return List.of(new DailyMetric("transaction_success", reportDate, 120L, "v1", "PASSED"));
        }
        @Override public List<DataQualityResult> listDataQuality(LocalDate dataDate, String jobCode, String ruleCode) {
            return List.of(new DataQualityResult("quality-1", "交易完整性", "rule-1", "PASSED", 100L, 0L, NOW));
        }
        @Override public List<MetricDefinition> listMetricDefinitions() {
            return List.of(new MetricDefinition("transaction_success", "v1", "交易成功量", "笔", "COUNT(transaction_id)"));
        }
        @Override public List<AlertRule> listAlertRules() {
            return List.of(new AlertRule("DUPLICATE_CHARGE", "重复扣款告警", "duplicate_charge_count", "CRITICAL",
                    "GT", 0L, true, 0L, "seed", NOW));
        }
        @Override public AlertRule updateAlertRuleThreshold(String operatorId, String ruleCode, long thresholdValue,
                                                           long version) {
            return new AlertRule(ruleCode, "重复扣款告警", "duplicate_charge_count", "CRITICAL",
                    "GT", thresholdValue, true, version + 1, operatorId, NOW.plusSeconds(1));
        }
    }

    /** 看板汇总替身只返回脱敏只读数据，避免 Controller 切片测试依赖数据库或网络探针。 */
    private static final class FakeDashboardService extends DashboardSummaryApplicationService {
        FakeDashboardService() { super(null, null, null); }

        @Override public DashboardSummary getSummary() {
            return new DashboardSummary(NOW,
                    new DashboardSummary.DashboardKpis(5200L, 9998L, 1L, 2L),
                    List.of(new RealtimeMetric("transaction.status.changed", NOW, 1L, "v1", "PASSED")),
                    List.of(new DataQualityResult("quality-1", "交易完整性", "rule-1", "PASSED", 100L, 0L, NOW)),
                    List.of(new DashboardSummary.ServiceHealth("gateway", "网关 gateway", DashboardSummary.ServiceHealthStatus.UP, 12L, NOW)),
                    List.of(FakeOpsQueryPort.row()));
        }
    }
}
