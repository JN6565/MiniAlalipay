package com.minialalipay.business.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * T+1 报表批处理端口，只读分析事件并在质量门禁通过后写入指标投影。
 *
 * <p>实现不得访问业务交易、账户或账本表；所有数据必须来自 {@code metrics_db.analytics_event}。</p>
 */
public interface DailyReportStore {
    /** 按事件类型和业务维度汇总指定日期的分析事件。 */
    List<DailyMetricValue> aggregate(LocalDate reportDate, String zoneId);

    /** 按闭区间起点、开区间终点汇总临时报表时间窗内的分析事件。 */
    default List<DailyMetricValue> aggregate(Instant from, Instant to) {
        throw new UnsupportedOperationException("当前日报存储不支持按时间窗聚合");
    }

    /** 返回指定业务日仍未完成的 Inbox 数量，避免当天在途事件阻塞历史日报。 */
    long countIncompleteInbox(LocalDate reportDate, String zoneId);

    /** 返回临时报表时间窗内仍未完成的 Inbox 数量。 */
    default long countIncompleteInbox(Instant from, Instant to) {
        throw new UnsupportedOperationException("当前日报存储不支持按时间窗检查 Inbox");
    }

    /** 返回时间窗内未完成 Inbox 的脱敏失败摘要，供质量门禁阻断报告定位影响范围。 */
    default List<InboxFailure> listIncompleteInboxFailures(Instant from, Instant to) {
        return List.of();
    }

    /** 返回指定日期进入隔离区的事件数量。 */
    long countQuarantined(LocalDate reportDate, String zoneId);

    /** 返回临时报表时间窗内进入隔离区的事件数量。 */
    default long countQuarantined(Instant from, Instant to) {
        throw new UnsupportedOperationException("当前日报存储不支持按时间窗检查隔离事件");
    }

    /** 将质量结果和日报指标一次提交；存在失败质量时不得写入可见日报。 */
    void publish(LocalDate reportDate, List<DailyMetricValue> metrics, List<QualityCheck> checks, Instant now);

    /** 待发布的日指标值及脱敏维度。 */
    record DailyMetricValue(String metricCode, byte[] dimensionHash, String dimensionsJson,
                            long value, int version) { }

    /** 日报质量门禁检查及证据。 */
    record QualityCheck(String ruleCode, String status, long checkedCount, long failedCount,
                        String evidenceJson) { }

    /** 单条未完成事件的可展示摘要，不包含原始载荷或敏感字段。 */
    record InboxFailure(String eventId, String reason, int retryCount, String status) { }
}
