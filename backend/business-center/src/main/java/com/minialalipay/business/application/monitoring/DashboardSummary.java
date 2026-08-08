package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.OpsTransactionQueryPort.OpsTransactionRow;
import com.minialalipay.business.domain.monitoring.DataQualityResult;
import com.minialalipay.business.domain.monitoring.RealtimeMetric;

import java.time.Instant;
import java.util.List;

/**
 * B 端可信运行看板的只读汇总视图。
 *
 * <p>该视图只组合统一交易、监控投影和服务健康探针，不修改余额、账本、交易或告警状态。
 * 今日范围按上海业务日计算；成功率以具有确定结果的交易为分母，只有 {@code SUCCESS} 计入成功。</p>
 */
public record DashboardSummary(
        Instant generatedAt,
        DashboardKpis kpis,
        List<RealtimeMetric> transactionTrend,
        List<DataQualityResult> dataQuality,
        List<ServiceHealth> services,
        List<OpsTransactionRow> recentTransactions) {

    /** 看板顶部四项经营与运行指标；金额单位均为分，成功率单位为万分比。 */
    public record DashboardKpis(long todayTransactionAmountFen, long paymentSuccessRateBps,
                                long pendingManualCaseCount, long openAlertCount) { }

    /** 单个受控依赖的健康探针结果；探针延迟只表示本次检查耗时，不冒充 P99 延迟。 */
    public record ServiceHealth(String serviceCode, String serviceName, ServiceHealthStatus status,
                                Long probeLatencyMs, Instant checkedAt) { }

    /** 服务健康状态；UNKNOWN 表示探针无法取得足够证据，页面不得按正常或故障推断。 */
    public enum ServiceHealthStatus {
        /** 服务本次健康检查返回可用。 */
        UP,
        /** 服务本次健康检查明确返回不可用。 */
        DOWN,
        /** 探针超时、网络异常或未配置检查目标，无法判定。 */
        UNKNOWN
    }
}
