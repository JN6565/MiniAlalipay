package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.monitoring.Alert;
import com.minialalipay.business.domain.monitoring.AlertRule;
import com.minialalipay.business.domain.monitoring.DataQualityResult;
import com.minialalipay.business.domain.monitoring.DailyMetric;
import com.minialalipay.business.domain.monitoring.MetricDefinition;
import com.minialalipay.business.domain.monitoring.RealtimeMetric;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 监控投影查询与告警处置应用服务。
 *
 * <p>处置只更新运营告警自身的操作人、理由和版本，不调用统一交易、TCC 或账户端口。实时指标必须携带合法
 * 时间范围；报表未通过质量门禁时不返回数值。</p>
 */
@Service
public class MonitoringApplicationService {
    /** 未指定实时指标时间范围时的默认回看窗口（PRD：实时概览默认最近 60 分钟）。 */
    private static final long DEFAULT_REALTIME_WINDOW_SECONDS = 60 * 60;

    private final MonitoringProjectionStore store;
    private final SecurityMaterialPort secure;
    private final Clock clock;

    /** 创建监控应用服务。 */
    @Autowired
    public MonitoringApplicationService(MonitoringProjectionStore store, SecurityMaterialPort secure) {
        this(store, secure, Clock.systemUTC());
    }

    MonitoringApplicationService(MonitoringProjectionStore store, SecurityMaterialPort secure, Clock clock) {
        this.store = store;
        this.secure = secure;
        this.clock = clock;
    }

    /** 查询运营可见告警；状态与级别均可选筛选项。 */
    @Transactional(readOnly = true)
    public List<Alert> listAlerts(String status, String severity, String cursor, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("告警分页数量必须在 1 到 100 之间");
        return store.listAlerts(status, severity, cursor, limit);
    }

    /** 确认开放告警；同操作人同键重试保持幂等。 */
    @Transactional
    public Alert acknowledgeAlert(String operatorId, String alertId, long version, String reason, String idempotencyKey) {
        return decideAlert(operatorId, alertId, AlertAction.ACKNOWLEDGE, version, reason, null, idempotencyKey);
    }

    /** 记录有证据支持的告警恢复结果。 */
    @Transactional
    public Alert resolveAlert(String operatorId, String alertId, long version, String reason,
                              String evidence, String idempotencyKey) {
        return decideAlert(operatorId, alertId, AlertAction.RESOLVE, version, reason, evidence, idempotencyKey);
    }

    /** 以最终证据关闭已恢复告警。 */
    @Transactional
    public Alert closeAlert(String operatorId, String alertId, long version, String reason,
                            String evidence, String idempotencyKey) {
        return decideAlert(operatorId, alertId, AlertAction.CLOSE, version, reason, evidence, idempotencyKey);
    }

    /** 查询分钟级实时指标；时间范围不合法时拒绝。 */
    @Transactional(readOnly = true)
    public List<RealtimeMetric> listRealtimeMetrics(String metricCode, Instant from, Instant to) {
        Instant start = from != null ? from : clock.instant().minusSeconds(DEFAULT_REALTIME_WINDOW_SECONDS);
        Instant end = to != null ? to : clock.instant();
        if (start.isAfter(end)) throw new BusinessException(BusinessErrorCode.INVALID_TIME_RANGE);
        return store.listRealtimeMetrics(metricCode, start, end);
    }

    /** 查询通过质量门禁的 T+1 报表；未发布时返回 404。 */
    @Transactional(readOnly = true)
    public List<DailyMetric> listDailyReports(LocalDate reportDate) {
        List<DailyMetric> reports = store.listDailyReports(reportDate);
        if (reports.isEmpty()) throw new BusinessException(BusinessErrorCode.REPORT_NOT_PUBLISHED);
        return reports;
    }

    /** 查询数据质量结果。 */
    @Transactional(readOnly = true)
    public List<DataQualityResult> listDataQuality(LocalDate dataDate, String jobCode, String ruleCode) {
        return store.listDataQuality(dataDate, jobCode, ruleCode);
    }

    /** 查询当前激活的指标口径定义。 */
    @Transactional(readOnly = true)
    public List<MetricDefinition> listMetricDefinitions() {
        return store.listMetricDefinitions();
    }

    /** 查询全部告警规则及阈值配置。 */
    @Transactional(readOnly = true)
    public List<AlertRule> listAlertRules() {
        return store.listAlertRules();
    }

    /**
     * 按版本 CAS 更新告警规则阈值并记录操作者。
     *
     * <p>阈值更新是幂等写操作（重复设置同一阈值无副作用），由规则版本 CAS 保证并发安全，
     * 不需要额外幂等表；规则结构不可变更，未知规则按资源不存在返回。</p>
     */
    @Transactional
    public AlertRule updateAlertRuleThreshold(String operatorId, String ruleCode, long thresholdValue,
                                              long version) {
        AlertRule rule = store.findAlertRule(ruleCode)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        AlertRule next = rule.withThreshold(thresholdValue, operatorId, clock.instant());
        if (!store.updateAlertRuleThreshold(next, version)) {
            throw new BusinessException(BusinessErrorCode.ALERT_STATE_INVALID);
        }
        return next;
    }

    private Alert decideAlert(String operatorId, String alertId, AlertAction action, long version,
                              String reason, String evidence, String idempotencyKey) {
        if (action != AlertAction.ACKNOWLEDGE && (evidence == null || evidence.isBlank())) {
            throw new BusinessException(BusinessErrorCode.EVIDENCE_REQUIRED);
        }
        byte[] requestHash = secure.digest(canonicalAlert(alertId, action, version, reason, evidence));
        MonitoringProjectionStore.AlertOpsIdempotencyRecord existing =
                store.findAlertOpsIdempotency(operatorId, idempotencyKey).orElse(null);
        if (existing != null) return replay(existing, requestHash);

        if (!store.reserveAlertOpsIdempotency(secure.newId(), operatorId, idempotencyKey, requestHash)) {
            return replay(store.findAlertOpsIdempotency(operatorId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("告警幂等占位冲突后未读取到既有记录")), requestHash);
        }
        Alert alert = store.findAlert(alertId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ALERT_NOT_FOUND));
        try {
            switch (action) {
                case ACKNOWLEDGE -> alert.acknowledge(operatorId, version, reason, clock.instant());
                case RESOLVE -> alert.resolve(operatorId, version, reason, clock.instant());
                case CLOSE -> alert.close(operatorId, version, reason, clock.instant());
            }
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(BusinessErrorCode.EVIDENCE_REQUIRED);
        } catch (IllegalStateException invalid) {
            if (invalid.getMessage() != null && invalid.getMessage().contains("版本")) {
                throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
            }
            throw new BusinessException(BusinessErrorCode.ALERT_STATE_INVALID);
        }
        if (!store.updateAlert(alert, version)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        store.completeAlertOpsIdempotency(operatorId, idempotencyKey, alert);
        return alert;
    }

    private Alert replay(MonitoringProjectionStore.AlertOpsIdempotencyRecord existing, byte[] requestHash) {
        if (!Arrays.equals(existing.requestHash(), requestHash)) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (existing.result() == null) {
            throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        }
        return existing.result();
    }

    private static String canonicalAlert(String alertId, AlertAction action, long version,
                                         String reason, String evidence) {
        return alertId + "\n" + action.name() + "\n" + version + "\n"
                + valueOrEmpty(reason) + "\n" + valueOrEmpty(evidence);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 告警处置命令类型，与 OpenAPI 的处置动作对应。 */
    public enum AlertAction {
        /** 确认开放告警。 */
        ACKNOWLEDGE,
        /** 记录有证据支持的恢复结果。 */
        RESOLVE,
        /** 以最终证据关闭告警。 */
        CLOSE
    }
}
