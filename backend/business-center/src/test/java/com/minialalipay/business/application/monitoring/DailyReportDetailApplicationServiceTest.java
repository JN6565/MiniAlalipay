package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 日报详情聚合服务测试，验证数据来源边界、环比计算和各详情分区映射。 */
class DailyReportDetailApplicationServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 8);
    private static final Instant NOW = Instant.parse("2026-08-09T01:00:00Z");

    @Test
    void 聚合已发布日报的总览趋势对账质量和告警详情() {
        MonitoringProjectionStore store = mock(MonitoringProjectionStore.class);
        when(store.listDailyReports(DATE)).thenReturn(List.of(new DailyMetric("PAYMENT_SUCCESS_RATE", DATE, 9998, "v1", "PASSED")));
        when(store.findDailyReportMetadata(DATE)).thenReturn(Optional.of(
                new MonitoringProjectionStore.DailyReportMetadata(NOW, "v1")));
        when(store.dailyReportTransactionStats(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(
                new MonitoringProjectionStore.DailyReportTransactionStats(100, 12000, 9900, 180, 80, 9000, 9800, 200));
        when(store.dailyReportTrend(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new MonitoringProjectionStore.DailyReportTrendPoint(NOW, 10, 1200)));
        when(store.listDailyReportReconciliation(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new MonitoringProjectionStore.DailyReportReconciliation("V-1", "T-1", NOW, 1200, "AMOUNT_MISMATCH", "OPEN")));
        when(store.listDailyReportQuality(DATE)).thenReturn(List.of(
                new MonitoringProjectionStore.DailyReportQuality("INBOX_COMPLETE", "事件消费完整性",
                        BigDecimal.ZERO, BigDecimal.ZERO, "PASSED")));
        when(store.listDailyReportAlerts(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new MonitoringProjectionStore.DailyReportAlert("A-1", "P1", "对账差异", NOW, "转人工确认台", "OPEN")));

        DailyReportDetailApplicationService service = new DailyReportDetailApplicationService(
                store, Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("Asia/Shanghai"));

        DailyReportDetailApplicationService.DailyReportDetail detail = service.get(DATE);

        assertThat(detail.reportMeta().reportVersion()).isEqualTo("v1");
        assertThat(detail.reportMeta().dataSources()).containsExactly(
                "metrics_db.monitoring_transaction_final_projection", "metrics_db.quality_result", "metrics_db.monitor_alert");
        assertThat(detail.overview().transactionCount()).isEqualTo(100);
        assertThat(detail.overview().dayOverDayChanges().transactionCountBps()).isEqualTo(2500);
        assertThat(detail.transactionTrend()).hasSize(1);
        assertThat(detail.reconciliation().getFirst().voucherNo()).isEqualTo("V-1");
        assertThat(detail.quality().getFirst().conclusion()).isEqualTo("PASSED");
        assertThat(detail.alerts().getFirst().status()).isEqualTo("OPEN");
    }
}
