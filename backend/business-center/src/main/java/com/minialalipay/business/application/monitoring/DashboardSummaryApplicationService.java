package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.application.port.OpsTransactionQueryPort;
import com.minialalipay.business.application.port.ServiceHealthProbe;
import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionQuery;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 可信运行看板的只读查询应用服务。
 *
 * <p>这里在一次请求内组合各限界上下文已经发布的只读事实；不以分页交易列表估算聚合数，
 * 避免数据量超过页面大小时产生不完整的成功率或交易额。</p>
 */
@Service
public class DashboardSummaryApplicationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int RECENT_TRANSACTION_LIMIT = 5;
    private static final long TREND_WINDOW_SECONDS = 60 * 60;

    private final MonitoringProjectionStore monitoringStore;
    private final OpsTransactionQueryPort transactionStore;
    private final ServiceHealthProbe healthProbe;
    private final Clock clock;

    /** 创建看板汇总查询服务。 */
    @Autowired
    public DashboardSummaryApplicationService(MonitoringProjectionStore monitoringStore,
                                              OpsTransactionQueryPort transactionStore,
                                              ServiceHealthProbe healthProbe) {
        this(monitoringStore, transactionStore, healthProbe, Clock.systemUTC());
    }

    DashboardSummaryApplicationService(MonitoringProjectionStore monitoringStore,
                                       OpsTransactionQueryPort transactionStore,
                                       ServiceHealthProbe healthProbe, Clock clock) {
        this.monitoringStore = monitoringStore;
        this.transactionStore = transactionStore;
        this.healthProbe = healthProbe;
        this.clock = clock;
    }

    /**
     * 查询当前运营可见的看板快照。
     *
     * <p>输入由服务端时钟确定，无客户端时间参数，避免客户端时区或时间篡改改变金融指标口径；
     * 探针不可用时仅对应服务返回 UNKNOWN，不影响已经持久化的交易与监控投影。</p>
     */
    @Transactional(readOnly = true)
    public DashboardSummary getSummary() {
        Instant now = clock.instant();
        LocalDate today = now.atZone(BUSINESS_ZONE).toLocalDate();
        Instant startOfToday = today.atStartOfDay(BUSINESS_ZONE).toInstant();
        OpsTransactionQueryPort.DashboardTransactionStats statistics =
                transactionStore.dashboardTransactionStats(startOfToday, now);

        return new DashboardSummary(
                now,
                new DashboardSummary.DashboardKpis(statistics.successAmountFen(), statistics.successRateBps(),
                        statistics.pendingManualCaseCount(), monitoringStore.countOpenAlerts()),
                monitoringStore.listRealtimeMetrics("transaction.status.changed", now.minusSeconds(TREND_WINDOW_SECONDS), now),
                monitoringStore.listDataQuality(today.minusDays(1), null, null),
                healthProbe.probeAll(),
                transactionStore.listTransactionsForOps(new OpsTransactionQuery(null, null, null, null,
                        RECENT_TRANSACTION_LIMIT, null, null)));
    }
}
