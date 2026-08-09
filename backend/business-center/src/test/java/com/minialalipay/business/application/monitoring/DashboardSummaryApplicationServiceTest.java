package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.application.port.OpsTransactionQueryPort;
import com.minialalipay.business.application.port.ServiceHealthProbe;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 可信运行看板统计口径测试。 */
class DashboardSummaryApplicationServiceTest {
    @Test
    void 交易金额与成功率使用最终交易投影而非业务表汇总() {
        Instant now = Instant.parse("2026-08-09T02:00:00Z");
        MonitoringProjectionStore monitoring = mock(MonitoringProjectionStore.class);
        OpsTransactionQueryPort transactions = mock(OpsTransactionQueryPort.class);
        ServiceHealthProbe healthProbe = mock(ServiceHealthProbe.class);
        when(monitoring.dailyReportTransactionStats(any(), any(), any(), any())).thenReturn(
                new MonitoringProjectionStore.DailyReportTransactionStats(4, 700, 7_500, 0, 0, 0, 0, 0));
        when(monitoring.countOpenAlerts()).thenReturn(2L);
        when(monitoring.listRealtimeMetrics(any(), any(), any())).thenReturn(List.of());
        when(monitoring.listDataQuality(any(), any(), any())).thenReturn(List.of());
        when(transactions.dashboardTransactionStats(any(), any())).thenReturn(
                new OpsTransactionQueryPort.DashboardTransactionStats(9_999, 1_000, 3, 4));
        when(transactions.listTransactionsForOps(any())).thenReturn(List.of());
        when(healthProbe.probeAll()).thenReturn(List.of());

        DashboardSummaryApplicationService service = new DashboardSummaryApplicationService(
                monitoring, transactions, healthProbe, Clock.fixed(now, ZoneOffset.UTC));

        DashboardSummary.DashboardKpis kpis = service.getSummary().kpis();

        assertThat(kpis.todayTransactionAmountFen()).isEqualTo(700L);
        assertThat(kpis.paymentSuccessRateBps()).isEqualTo(7_500L);
        assertThat(kpis.pendingManualCaseCount()).isEqualTo(3L);
        verify(monitoring).dailyReportTransactionStats(any(), any(), any(), any());
    }
}
