package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * T+1 日报详情聚合服务。
 *
 * <p>日报详情只消费 metrics_db 中的分析事件和运营投影，
 * 通过应用层组合交易总览、趋势、质量、告警和脱敏对账事件，避免回查业务交易表或跨库访问 account-center 私有表。</p>
 */
@Service
public class DailyReportDetailApplicationService {
    private final MonitoringProjectionStore monitoring;
    private final Clock clock;
    private final ZoneId zone;

    /** 创建日报详情查询服务；业务日期和时间窗口统一由服务端上海时区确定。 */
    @Autowired
    public DailyReportDetailApplicationService(MonitoringProjectionStore monitoring,
                                                @Value("${minialalipay.monitoring.report.zone:Asia/Shanghai}") String zoneId) {
        this(monitoring, Clock.systemUTC(), ZoneId.of(zoneId));
    }

    DailyReportDetailApplicationService(MonitoringProjectionStore monitoring, Clock clock, ZoneId zone) {
        this.monitoring = monitoring;
        this.clock = clock;
        this.zone = zone;
    }

    /** 查询已通过发布门禁的日报详情；未发布日期返回契约定义的资源不存在错误。 */
    @Transactional(readOnly = true)
    public DailyReportDetail get(LocalDate reportDate) {
        LocalDate date = reportDate == null ? LocalDate.now(clock.withZone(zone)).minusDays(1) : reportDate;
        if (date.isAfter(LocalDate.now(clock.withZone(zone)).minusDays(1))) {
            throw new BusinessException(BusinessErrorCode.REPORT_NOT_PUBLISHED);
        }
        List<DailyMetric> metrics = monitoring.listDailyReports(date);
        if (metrics.isEmpty()) throw new BusinessException(BusinessErrorCode.REPORT_NOT_PUBLISHED);

        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        Instant previousFrom = date.minusDays(1).atStartOfDay(zone).toInstant();
        Instant previousTo = from;
        MonitoringProjectionStore.DailyReportTransactionStats stats = monitoring.dailyReportTransactionStats(from, to, previousFrom, previousTo);
        MonitoringProjectionStore.DailyReportTransactionStats emptySafe = stats == null
                ? new MonitoringProjectionStore.DailyReportTransactionStats(0, 0, 0, 0, 0, 0, 0, 0) : stats;
        MonitoringProjectionStore.DailyReportMetadata metadata = monitoring.findDailyReportMetadata(date)
                .orElse(new MonitoringProjectionStore.DailyReportMetadata(clock.instant(), "v1"));

        return new DailyReportDetail(
                new ReportMeta(date, metadata.generatedAt(), metadata.reportVersion(), new ReportWindow(from, to),
                        List.of("metrics_db.monitoring_transaction_final_projection", "metrics_db.quality_result", "metrics_db.monitor_alert")),
                metrics,
                new Overview(emptySafe.transactionCount(), emptySafe.transactionAmountFen(), emptySafe.successRateBps(),
                        emptySafe.averageLatencyMs(), new DayOverDayChanges(
                        deltaBps(emptySafe.transactionCount(), emptySafe.previousTransactionCount()),
                        deltaBps(emptySafe.transactionAmountFen(), emptySafe.previousTransactionAmountFen()),
                        deltaBps(emptySafe.successRateBps(), emptySafe.previousSuccessRateBps()),
                        deltaBps(emptySafe.averageLatencyMs(), emptySafe.previousAverageLatencyMs()))),
                monitoring.dailyReportTrend(from, to).stream().map(TrendPoint::from).toList(),
                monitoring.listDailyReportReconciliation(from, to).stream().map(Reconciliation::from).toList(),
                monitoring.listDailyReportQuality(date).stream().map(Quality::from).toList(),
                monitoring.listDailyReportAlerts(from, to).stream().map(AlertSummary::from).toList());
    }

    private static long deltaBps(long current, long previous) {
        if (previous == 0L) return 0L;
        return (current - previous) * 10_000L / previous;
    }

    /** 日报详情完整响应。 */
    public record DailyReportDetail(ReportMeta reportMeta, List<DailyMetric> metrics, Overview overview,
                                    List<TrendPoint> transactionTrend, List<Reconciliation> reconciliation,
                                    List<Quality> quality, List<AlertSummary> alerts) { }

    /** 日报元信息。 */
    public record ReportMeta(LocalDate reportDate, Instant generatedAt, String reportVersion,
                             ReportWindow dataWindow, List<String> dataSources) { }

    /** 日报业务时间窗口。 */
    public record ReportWindow(Instant from, Instant to) { }

    /** 交易总览卡片数据。 */
    public record Overview(long transactionCount, long transactionAmountFen, long successRateBps,
                           long averageLatencyMs, DayOverDayChanges dayOverDayChanges) { }

    /** 交易总览相对上一业务日的变化，单位为基点。 */
    public record DayOverDayChanges(long transactionCountBps, long transactionAmountBps,
                                    long successRateBps, long averageLatencyBps) { }

    /** 最终成功交易按整点小时聚合的趋势分桶数据。 */
    public record TrendPoint(Instant timeBucket, long transactionCount, long amountFen) {
        static TrendPoint from(MonitoringProjectionStore.DailyReportTrendPoint point) {
            return new TrendPoint(point.timeBucket(), point.transactionCount(), point.amountFen());
        }
    }

    /** 脱敏对账差异明细。 */
    public record Reconciliation(String voucherNo, String transactionId, Instant occurredAt,
                                 long amountFen, String differenceType, String status) {
        static Reconciliation from(MonitoringProjectionStore.DailyReportReconciliation row) {
            return new Reconciliation(row.voucherNo(), row.transactionId(), row.occurredAt(), row.amountFen(),
                    row.differenceType(), row.status());
        }
    }

    /** 数据质量维度结果。 */
    public record Quality(String dimension, String definition, java.math.BigDecimal currentValue,
                          java.math.BigDecimal threshold, String conclusion) {
        static Quality from(MonitoringProjectionStore.DailyReportQuality row) {
            return new Quality(row.dimension(), row.definition(), row.currentValue(), row.threshold(), row.conclusion());
        }
    }

    /** 告警汇总行。 */
    public record AlertSummary(String alertId, String level, String content, Instant occurredAt,
                               String action, String status) {
        static AlertSummary from(MonitoringProjectionStore.DailyReportAlert row) {
            return new AlertSummary(row.alertId(), row.level(), row.content(), row.occurredAt(), row.action(), row.status());
        }
    }
}
