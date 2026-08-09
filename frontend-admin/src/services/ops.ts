import { gatewayRequest } from './request';

/**
 * B 端监控运维、人工工单与演示任务网关服务。
 *
 * 只访问网关公开前缀（/api/v1），统一走 gatewayRequest 注入 X-Request-Id 并归一化错误。
 * 金额统一为整数分；写操作全部携带服务端幂等键。
 */

/** 与 OpenAPI ApiResponse 对齐的统一响应外壳。 */
export interface ApiResponse<T> {
  /** 稳定结果码。 */
  code: string;
  /** 面向调用方的中文说明。 */
  message: string;
  /** 请求编号。 */
  requestId?: string;
  /** 链路编号。 */
  traceId?: string;
  /** 业务数据。 */
  data: T;
}

/** 告警投影行，与 OpenAPI Alert 对齐。 */
export interface AlertItem {
  alertId: string;
  alertType: string;
  severity: string;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'CLOSED';
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** 告警分页，与 OpenAPI AlertPage 对齐。 */
export interface AlertPage {
  items: AlertItem[];
  nextCursor: string | null;
}

/** 数据质量结果，与 OpenAPI DataQualityResult 对齐。 */
export interface DataQualityItem {
  resultId: string;
  taskCode: string;
  ruleCode: string;
  status: string;
  checkedCount: number;
  failedCount: number;
  completedAt: string;
}

/** 日报指标，与 OpenAPI DailyMetric 对齐。 */
export interface DailyMetricItem {
  metricCode: string;
  reportDate: string;
  value: number;
  metricVersion: string;
  qualityStatus: string;
}

/** 实时指标，与 OpenAPI RealtimeMetric 对齐。 */
export interface RealtimeMetricItem {
  metricCode: string;
  bucketAt: string;
  value: number;
  metricVersion: string;
  qualityStatus: string;
}

/** 临时报表预览；仅表示服务端计算时刻的快照，不会替代已发布的 T+1 日报。 */
export interface DailyReportPreview {
  windowStart: string;
  windowEnd: string;
  status: 'READY' | 'BLOCKED';
  metrics: Array<{ metricCode: string; value: number; metricVersion: string }>;
  qualityChecks: Array<{ ruleCode: string; status: 'PASSED' | 'FAILED'; checkedCount: number; failedCount: number }>;
  failures: Array<{ eventId: string; reason: string; retryCount: number; status: string }>;
}

/** 正式日报重生成结果；门禁阻断时不返回指标值。 */
export interface DailyReportGeneration {
  reportDate: string;
  status: 'PUBLISHED' | 'BLOCKED';
  generatedAt: string;
  metrics: Array<{ metricCode: string; value: number; metricVersion: string }>;
  qualityChecks: Array<{ ruleCode: string; status: 'PASSED' | 'FAILED'; checkedCount: number; failedCount: number }>;
  failures: Array<{ eventId: string; reason: string; retryCount: number; status: string }>;
}

/** 可信运行看板顶部四项只读指标；金额单位为分，成功率单位为万分比。 */
export interface DashboardKpis {
  todayTransactionAmountFen: number;
  paymentSuccessRateBps: number;
  pendingManualCaseCount: number;
  openAlertCount: number;
}

/** 看板服务探针状态；UNKNOWN 表示无法取得足够证据，前端不得按正常或故障推断。 */
export interface DashboardServiceHealth {
  serviceCode: 'gateway' | 'account-center' | 'redis' | 'ai-service';
  serviceName: string;
  status: 'UP' | 'DOWN' | 'UNKNOWN';
  probeLatencyMs: number | null;
  checkedAt: string;
}

/** 可信运行看板的服务端只读汇总投影。 */
export interface DashboardSummary {
  generatedAt: string;
  kpis: DashboardKpis;
  transactionTrend: RealtimeMetricItem[];
  dataQuality: DataQualityItem[];
  services: DashboardServiceHealth[];
  recentTransactions: OpsTransactionItem[];
}

/** 人工工单行，与 OpenAPI ManualCase 对齐；处置后暴露操作者、理由与证据引用等审计事实。 */
export interface ManualCaseItem {
  caseId: string;
  caseType: string;
  subjectType: string;
  subjectId: string;
  status: 'OPEN' | 'CLAIMED' | 'RESOLVED' | 'CLOSED';
  reasonCode: string;
  operatorId: string | null;
  lastReason: string | null;
  evidenceReference: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

/** 人工工单分页，与 OpenAPI ManualCasePage 对齐。 */
export interface ManualCasePage {
  items: ManualCaseItem[];
  nextCursor: string | null;
}

/** B 端脱敏交易摘要，与 OpenAPI OpsTransaction 对齐；不暴露完整用户或账户标识。 */
export interface OpsTransactionItem {
  transactionId: string;
  businessType: string;
  sourceType: string;
  sourceOrderId: string;
  initiatorMasked: string;
  amountFen: number;
  status: string;
  riskLevel: string;
  traceId: string;
  createdAt: string;
  updatedAt: string;
}

/** 交易分页，与 OpenAPI OpsTransactionPage 对齐。 */
export interface OpsTransactionPage {
  items: OpsTransactionItem[];
  nextCursor: string | null;
}

/** 单笔交易详情，与 OpenAPI OpsTransactionDetail 对齐。 */
export interface OpsTransactionDetailItem {
  transaction: OpsTransactionItem;
  fundingSource: string;
  tccStatus: string | null;
  tccRetryCount: number;
  latestOutboxEventType: string | null;
  outboxStatus: string | null;
  activeManualCaseId: string | null;
}

/** 链路追溯片段，与 OpenAPI TraceSpan 对齐；transactionId 表示片段归属的交易号，非交易归属的服务片段为空。 */
export interface TraceSpanItem {
  service: string;
  operation: string;
  status: string;
  detail: string;
  traceId: string;
  occurredAt: string;
  transactionId?: string;
}

/** 告警规则及阈值配置，与 OpenAPI AlertRule 对齐。 */
export interface AlertRuleItem {
  ruleCode: string;
  ruleName: string;
  metricCode: string;
  severity: 'CRITICAL' | 'WARNING' | 'INFO';
  operator: 'GT' | 'GTE' | 'LT' | 'LTE';
  thresholdValue: number;
  enabled: boolean;
  version: number;
  updatedBy: string;
  updatedAt: string;
}

/** 指标口径定义，与 OpenAPI MetricDefinition 对齐。 */
export interface MetricDefinitionItem {
  metricCode: string;
  version: string;
  name: string;
  unit: string;
  formula?: string;
}

/** 信用运维任务运行记录，与 OpenAPI CreditJobRun 对齐。 */
export interface CreditJobRun {
  runId: string;
  jobType: 'STATEMENT' | 'DUE_CHECK';
  businessDate: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'MANUAL_REVIEW';
  startedAt?: string | null;
  completedAt?: string | null;
  errorCode?: string | null;
}

/** 写操作统一携带随机幂等键，避免重复提交产生重复业务结果。 */
function writeAction<T>(url: string, data: Record<string, unknown>): Promise<ApiResponse<T>> {
  return gatewayRequest<ApiResponse<T>>(url, {
    method: 'POST',
    data,
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  });
}

/** 查询告警投影，支持状态与级别（severity）筛选及稳定游标分页。 */
export function listAlerts(
  status?: string,
  severity?: string,
  cursor?: string,
  limit = 50,
): Promise<ApiResponse<AlertPage>> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (status) params.set('status', status);
  if (severity) params.set('severity', severity);
  if (cursor) params.set('cursor', cursor);
  return gatewayRequest<ApiResponse<AlertPage>>(`/api/v1/ops/alerts?${params.toString()}`);
}

/** 确认开放告警。 */
export function acknowledgeAlert(alertId: string, version: number, reason: string): Promise<ApiResponse<AlertItem>> {
  return writeAction(`/api/v1/ops/alerts/${alertId}/acknowledge`, { version, reason });
}

/** 恢复已确认告警。 */
export function resolveAlert(alertId: string, version: number, reason: string, evidence: string): Promise<ApiResponse<AlertItem>> {
  return writeAction(`/api/v1/ops/alerts/${alertId}/resolve`, { version, reason, evidence });
}

/** 关闭已恢复告警。 */
export function closeAlert(alertId: string, version: number, reason: string, evidence: string): Promise<ApiResponse<AlertItem>> {
  return writeAction(`/api/v1/ops/alerts/${alertId}/close`, { version, reason, evidence });
}

/** 查询数据质量结果，支持按数据日期、任务编码与规则编码筛选。 */
export function listDataQuality(dataDate?: string, jobCode?: string, ruleCode?: string): Promise<ApiResponse<DataQualityItem[]>> {
  const params = new URLSearchParams();
  if (dataDate) params.set('dataDate', dataDate);
  if (jobCode) params.set('jobCode', jobCode);
  if (ruleCode) params.set('ruleCode', ruleCode);
  return gatewayRequest<ApiResponse<DataQualityItem[]>>(`/api/v1/ops/data-quality?${params.toString()}`);
}

/** 查询通过质量门禁的 T+1 报表。 */
export function listDailyReports(reportDate?: string): Promise<ApiResponse<DailyMetricItem[]>> {
  const params = new URLSearchParams();
  if (reportDate) params.set('reportDate', reportDate);
  return gatewayRequest<ApiResponse<DailyMetricItem[]>>(`/api/v1/ops/daily-reports?${params.toString()}`);
}

/** 查询分钟级实时指标；可指定指标代码与时间范围，未指定范围由服务端回看默认窗口（60 分钟）。 */
export function listRealtimeMetrics(
  metricCode?: string,
  from?: string,
  to?: string,
): Promise<ApiResponse<RealtimeMetricItem[]>> {
  const params = new URLSearchParams();
  if (metricCode) params.set('metricCode', metricCode);
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  return gatewayRequest<ApiResponse<RealtimeMetricItem[]>>(`/api/v1/ops/realtime-metrics?${params.toString()}`);
}

/** 生成昨日零点至当前时刻的临时报表预览，仅管理员可调用。 */
export function generateDailyReportPreview(): Promise<ApiResponse<DailyReportPreview>> {
  return gatewayRequest<ApiResponse<DailyReportPreview>>('/api/v1/ops/daily-report-previews', { method: 'POST' });
}

/** 按已结束业务日重新生成正式日报，服务端负责质量门禁和幂等发布。 */
export function generateDailyReport(reportDate: string): Promise<ApiResponse<DailyReportGeneration>> {
  return gatewayRequest<ApiResponse<DailyReportGeneration>>(
    `/api/v1/ops/daily-reports/${encodeURIComponent(reportDate)}/generate`, { method: 'POST' },
  );
}

/** 查询可信运行看板汇总；数据由服务端按统一口径聚合，前端不得自行估算交易金额或成功率。 */
export function getDashboardSummary(): Promise<ApiResponse<DashboardSummary>> {
  return gatewayRequest<ApiResponse<DashboardSummary>>('/api/v1/ops/dashboard-summary');
}

/** 查询运营可见人工工单，支持按状态、类型筛选与稳定游标分页。 */
export function listManualCases(
  status?: string,
  type?: string,
  cursor?: string,
  limit = 50,
): Promise<ApiResponse<ManualCasePage>> {
  const params = new URLSearchParams({ limit: String(limit) });
  if (status) params.set('status', status);
  if (type) params.set('type', type);
  if (cursor) params.set('cursor', cursor);
  return gatewayRequest<ApiResponse<ManualCasePage>>(`/api/v1/manual-cases?${params.toString()}`);
}

/** 处置人工工单（领取/解决/重开/关闭），带服务端幂等键。 */
export function decideManualCase(
  caseId: string,
  decision: 'CLAIM' | 'RESOLVE' | 'REOPEN' | 'CLOSE',
  version: number,
  reason?: string,
  evidence?: string,
): Promise<ApiResponse<ManualCaseItem>> {
  return writeAction(`/api/v1/manual-cases/${caseId}/decisions`, { decision, version, reason, evidence });
}

/** 触发信用出账任务（仅系统管理员的受审计动作，系统分析 16.7）。 */
export function runCreditStatement(businessDate: string): Promise<ApiResponse<CreditJobRun>> {
  return writeAction('/api/v1/ops/credit/statement-runs', { businessDate });
}

/** 触发信用到期检查任务（仅系统管理员的受审计动作，系统分析 16.7）。 */
export function runCreditDueCheck(businessDate: string): Promise<ApiResponse<CreditJobRun>> {
  return writeAction('/api/v1/ops/credit/due-check-runs', { businessDate });
}

/** 查询历史指标口径版本，供报表按 metricCode 展示名称与单位。 */
export function listMetricDefinitions(): Promise<ApiResponse<MetricDefinitionItem[]>> {
  return gatewayRequest<ApiResponse<MetricDefinitionItem[]>>('/api/v1/ops/metric-definitions');
}

/** 分页查询全平台脱敏交易摘要；金额为整数分，仅展示服务端确定的资金事实。 */
export function listOpsTransactions(
  params?: { status?: string; businessType?: string; initiator?: string; cursor?: string; limit?: number },
): Promise<ApiResponse<OpsTransactionPage>> {
  const query = new URLSearchParams({ limit: String(params?.limit ?? 50) });
  if (params?.status) query.set('status', params.status);
  if (params?.businessType) query.set('businessType', params.businessType);
  if (params?.initiator) query.set('initiator', params.initiator);
  if (params?.cursor) query.set('cursor', params.cursor);
  return gatewayRequest<ApiResponse<OpsTransactionPage>>(`/api/v1/ops/transactions?${query.toString()}`);
}

/** 查询单笔脱敏交易详情。 */
export function getOpsTransaction(transactionId: string): Promise<ApiResponse<OpsTransactionDetailItem>> {
  return gatewayRequest<ApiResponse<OpsTransactionDetailItem>>(`/api/v1/ops/transactions/${encodeURIComponent(transactionId)}`);
}

/** 查询交易链路片段；按交易归属的链路编号返回跨服务脱敏 Span。 */
export function getOpsTransactionTrace(transactionId: string): Promise<ApiResponse<TraceSpanItem[]>> {
  return gatewayRequest<ApiResponse<TraceSpanItem[]>>(`/api/v1/ops/transactions/${encodeURIComponent(transactionId)}/trace`);
}

/** 按链路编号查询跨服务链路片段；无已核验片段时返回空列表。 */
export function getOpsTraceByTraceId(traceId: string): Promise<ApiResponse<TraceSpanItem[]>> {
  return gatewayRequest<ApiResponse<TraceSpanItem[]>>(`/api/v1/ops/traces/${encodeURIComponent(traceId)}`);
}

/** 查询全部告警规则及阈值配置。 */
export function listAlertRules(): Promise<ApiResponse<AlertRuleItem[]>> {
  return gatewayRequest<ApiResponse<AlertRuleItem[]>>('/api/v1/ops/alert-rules');
}

/** 按版本 CAS 更新告警规则阈值（管理员受审计动作）；阈值写入由版本 CAS 保证并发安全，无需幂等键。 */
export function updateAlertRuleThreshold(
  ruleCode: string,
  thresholdValue: number,
  version: number,
): Promise<ApiResponse<AlertRuleItem>> {
  return gatewayRequest<ApiResponse<AlertRuleItem>>(
    `/api/v1/ops/alert-rules/${encodeURIComponent(ruleCode)}/thresholds`,
    { method: 'POST', data: { thresholdValue, version } },
  );
}
