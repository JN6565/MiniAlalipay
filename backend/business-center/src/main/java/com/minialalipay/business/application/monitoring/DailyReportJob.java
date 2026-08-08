package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.DailyReportStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * T+1 日报批处理与质量门禁。
 *
 * <p>任务按上海业务日处理前一日事件，先写质量检查结果，再仅在完整性和隔离事件检查通过时发布
 * {@code daily_metric}。同一日期重复执行使用唯一键覆盖同一版本，结果可恢复。</p>
 */
@Service
@ConditionalOnProperty(name = "minialalipay.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class DailyReportJob {
    private final DailyReportStore store;
    private final Clock clock;
    private final ZoneId zone;

    @Autowired
    public DailyReportJob(DailyReportStore store,
                          @Value("${minialalipay.monitoring.report.zone:Asia/Shanghai}") String zoneId) {
        this(store, Clock.systemUTC(), ZoneId.of(zoneId));
    }

    DailyReportJob(DailyReportStore store, Clock clock, ZoneId zone) {
        this.store = store;
        this.clock = clock;
        this.zone = zone;
    }

    /** 每日 01:00 生成前一业务日的日报；失败门禁保留质量证据等待重跑。 */
    @Scheduled(cron = "${minialalipay.monitoring.report.cron:0 0 1 * * *}",
            zone = "${minialalipay.monitoring.report.zone:Asia/Shanghai}")
    @Transactional
    public void runPreviousBusinessDay() {
        run(LocalDate.now(clock.withZone(zone)).minusDays(1));
    }

    /** 供受控演示或恢复任务指定业务日重跑，不接受未来日期。 */
    @Transactional
    public DailyReportResult run(LocalDate reportDate) {
        if (reportDate == null || reportDate.isAfter(LocalDate.now(clock.withZone(zone)).minusDays(1))) {
            throw new IllegalArgumentException("日报日期必须是已结束的业务日");
        }
        List<DailyReportStore.DailyMetricValue> metrics = store.aggregate(reportDate, zone.getId());
        long incompleteInbox = store.countIncompleteInbox(reportDate, zone.getId());
        long quarantined = store.countQuarantined(reportDate, zone.getId());
        List<DailyReportStore.QualityCheck> checks = List.of(
                new DailyReportStore.QualityCheck("INBOX_COMPLETE", incompleteInbox == 0 ? "PASSED" : "FAILED",
                        incompleteInbox, incompleteInbox, "{\"incompleteInbox\":" + incompleteInbox + "}"),
                new DailyReportStore.QualityCheck("EVENT_QUARANTINE_EMPTY", quarantined == 0 ? "PASSED" : "FAILED",
                        quarantined, quarantined, "{\"quarantinedEvents\":" + quarantined + "}"));
        Instant generatedAt = clock.instant();
        store.publish(reportDate, metrics, checks, generatedAt);
        boolean passed = checks.stream().allMatch(check -> "PASSED".equals(check.status()));
        List<DailyReportStore.InboxFailure> failures = store.listIncompleteInboxFailures(
                reportDate.atStartOfDay(zone).toInstant(), reportDate.plusDays(1).atStartOfDay(zone).toInstant());
        return new DailyReportResult(reportDate, passed ? "PUBLISHED" : "BLOCKED",
                passed ? metrics : List.of(), checks, failures, generatedAt);
    }

    /**
     * 生成昨日零点至当前时刻的临时报表预览。
     *
     * <p>预览只读取分析事件和质量证据，绝不写入 {@code daily_metric} 或 {@code quality_result}，
     * 以免未结束业务日的数据覆盖通过 T+1 门禁发布的正式日报。</p>
     *
     * @return 包含服务端确定时间窗、质量状态和可展示指标的临时快照
     */
    @Transactional(readOnly = true)
    public DailyReportPreview previewFromPreviousBusinessDay() {
        Instant windowEnd = clock.instant();
        Instant windowStart = LocalDate.now(clock.withZone(zone)).minusDays(1).atStartOfDay(zone).toInstant();
        List<DailyReportStore.DailyMetricValue> metrics = store.aggregate(windowStart, windowEnd);
        long incompleteInbox = store.countIncompleteInbox(windowStart, windowEnd);
        long quarantined = store.countQuarantined(windowStart, windowEnd);
        List<DailyReportStore.QualityCheck> checks = List.of(
                new DailyReportStore.QualityCheck("INBOX_COMPLETE", incompleteInbox == 0 ? "PASSED" : "FAILED",
                        incompleteInbox, incompleteInbox, "{\"incompleteInbox\":" + incompleteInbox + "}"),
                new DailyReportStore.QualityCheck("EVENT_QUARANTINE_EMPTY", quarantined == 0 ? "PASSED" : "FAILED",
                        quarantined, quarantined, "{\"quarantinedEvents\":" + quarantined + "}"));
        boolean publishable = checks.stream().allMatch(check -> "PASSED".equals(check.status()));
        return new DailyReportPreview(windowStart, windowEnd, publishable, publishable ? metrics : List.of(), checks,
                store.listIncompleteInboxFailures(windowStart, windowEnd));
    }

    /** 临时报表预览的不可变计算结果。 */
    public record DailyReportPreview(Instant windowStart, Instant windowEnd, boolean publishable,
                                     List<DailyReportStore.DailyMetricValue> metrics,
                                     List<DailyReportStore.QualityCheck> checks,
                                     List<DailyReportStore.InboxFailure> failures) { }

    /** 正式日报生成结果；门禁阻断时不携带交易指标，避免展示未经确认的数据。 */
    public record DailyReportResult(LocalDate reportDate, String status,
                                    List<DailyReportStore.DailyMetricValue> metrics,
                                    List<DailyReportStore.QualityCheck> checks,
                                    List<DailyReportStore.InboxFailure> failures,
                                    Instant generatedAt) { }
}
