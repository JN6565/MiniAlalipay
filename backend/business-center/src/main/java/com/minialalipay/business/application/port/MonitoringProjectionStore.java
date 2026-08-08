package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.monitoring.Alert;
import com.minialalipay.business.domain.monitoring.AlertRule;
import com.minialalipay.business.domain.monitoring.DataQualityResult;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import com.minialalipay.business.domain.monitoring.MetricDefinition;
import com.minialalipay.business.domain.monitoring.RealtimeMetric;

import java.time.Instant;
import java.time.LocalDate;
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
}
