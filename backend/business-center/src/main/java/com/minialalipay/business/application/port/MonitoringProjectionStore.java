package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.monitoring.Alert;
import com.minialalipay.business.domain.monitoring.AlertRule;
import com.minialalipay.business.domain.monitoring.DataQualityResult;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import com.minialalipay.business.domain.monitoring.MetricDefinition;
import com.minialalipay.business.domain.monitoring.RealtimeMetric;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 监控投影的只读查询与告警处置端口。
 *
 * <p>实现读取 {@code metrics_db} 的告警、指标、报表和质量投影。处置只更新运营投影与审计信息，
 * 不得通过本端口修改交易、账户、冻结或账本事实。</p>
 */
public interface MonitoringProjectionStore {
    /** 按状态、级别和稳定游标查询运营可见告警；状态与级别均可选。 */
    List<Alert> listAlerts(String status, String severity, String cursor, int limit);

    /** 按 ID 查询告警。 */
    Optional<Alert> findAlert(String alertId);

    /** 按旧版本 CAS 持久化告警处置；影响行数为零时返回 false。 */
    boolean updateAlert(Alert alert, long expectedVersion);

    /** 查询指定时间窗口内分钟级实时指标；{@code metricCode} 为空时返回全部。 */
    List<RealtimeMetric> listRealtimeMetrics(String metricCode, Instant from, Instant to);

    /** 统计告警规则对应指标在时间窗口内的事件数量；只读取监控分析投影。 */
    default long countMetric(String metricCode, Instant from, Instant to) { return 0L; }

    /** 统计仍处于 OPEN 的运营告警数量，供看板展示待处置风险，不以分页列表长度代替总数。 */
    default long countOpenAlerts() { return 0L; }

    /** 查询同一规则当前仍需运营处置的活动告警，防止定时判定重复开单。 */
    default Optional<Alert> findActiveAlertByRule(String ruleCode) { return Optional.empty(); }

    /** 查询通过质量门禁的 T+1 日指标。 */
    List<DailyMetric> listDailyReports(LocalDate reportDate);

    /** 从按交易号去重的最终交易投影聚合日报总览，禁止回查业务交易表。 */
    default DailyReportTransactionStats dailyReportTransactionStats(Instant from, Instant to,
                                                                      Instant previousFrom, Instant previousTo) {
        return new DailyReportTransactionStats(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    /** 从最终成功交易投影聚合日报小时趋势，每个上海业务日整点只返回一个聚合点。 */
    default List<DailyReportTrendPoint> dailyReportTrend(Instant from, Instant to) { return List.of(); }

    /** 查询日报元信息；未发布或无质量检查记录时返回空。 */
    default Optional<DailyReportMetadata> findDailyReportMetadata(LocalDate reportDate) { return Optional.empty(); }

    /** 查询日报窗口内的脱敏对账差异事件，不读取 account-center 私有表。 */
    default List<DailyReportReconciliation> listDailyReportReconciliation(Instant from, Instant to) { return List.of(); }

    /** 查询日报质量维度、实际值、阈值和结论。 */
    default List<DailyReportQuality> listDailyReportQuality(LocalDate reportDate) { return List.of(); }

    /** 查询日报窗口内的告警汇总，只读取运营告警投影。 */
    default List<DailyReportAlert> listDailyReportAlerts(Instant from, Instant to) { return List.of(); }

    /** 按日期、任务和规则过滤数据质量结果。 */
    List<DataQualityResult> listDataQuality(LocalDate dataDate, String jobCode, String ruleCode);

    /** 查询当前激活的指标口径定义。 */
    List<MetricDefinition> listMetricDefinitions();

    /** 查询全部告警规则及阈值配置。 */
    List<AlertRule> listAlertRules();

    /** 按规则代码查询告警规则。 */
    Optional<AlertRule> findAlertRule(String ruleCode);

    /** 按旧版本 CAS 持久化告警规则阈值；影响行数为零时返回 false。 */
    boolean updateAlertRuleThreshold(AlertRule rule, long expectedVersion);

    /** 查询同一运营人员已完成的告警处置幂等结果。 */
    Optional<AlertOpsIdempotencyRecord> findAlertOpsIdempotency(String operatorId, String idempotencyKey);

    /**
     * 抢占告警处置幂等键。
     *
     * <p>必须与告警 CAS 更新、幂等结果快照在同一 {@code metrics_db} 本地事务中提交，确保进程崩溃或
     * 重复投递不会把同一处置执行两次。</p>
     */
    boolean reserveAlertOpsIdempotency(String recordId, String operatorId, String idempotencyKey, byte[] requestHash);

    /** 保存已完成告警处置的响应快照，供同键同参请求稳定回放。 */
    void completeAlertOpsIdempotency(String operatorId, String idempotencyKey, Alert alert);

    /** 告警处置幂等事实；{@code result} 为 null 表示正在由同一事务执行，调用方应安全重试。 */
    record AlertOpsIdempotencyRecord(byte[] requestHash, Alert result) { }

    /** 日报元信息；版本来自已发布指标的口径版本。 */
    record DailyReportMetadata(Instant generatedAt, String reportVersion) { }

    /** 最终交易投影聚合的交易总览；金额单位为分，成功率单位为万分比。 */
    record DailyReportTransactionStats(long transactionCount, long transactionAmountFen, long successRateBps,
                                       long averageLatencyMs, long previousTransactionCount,
                                       long previousTransactionAmountFen, long previousSuccessRateBps,
                                       long previousAverageLatencyMs) { }

    /** 最终成功交易按整点小时聚合的趋势分桶。 */
    record DailyReportTrendPoint(Instant timeBucket, long transactionCount, long amountFen) { }

    /** 脱敏对账差异行；凭证号可能因事件未携带而为空。 */
    record DailyReportReconciliation(String voucherNo, String transactionId, Instant occurredAt,
                                     long amountFen, String differenceType, String status) { }

    /** 日报质量维度；数值单位由指标定义决定。 */
    record DailyReportQuality(String dimension, String definition, BigDecimal currentValue,
                              BigDecimal threshold, String conclusion) { }

    /** 日报告警摘要。 */
    record DailyReportAlert(String alertId, String level, String content, Instant occurredAt,
                            String action, String status) { }
}
