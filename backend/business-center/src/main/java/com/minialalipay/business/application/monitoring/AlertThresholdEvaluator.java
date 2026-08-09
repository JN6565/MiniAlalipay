package com.minialalipay.business.application.monitoring;

import com.minialalipay.business.application.port.MonitoringProjectionStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.monitoring.AlertRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 按告警规则计算已投影实时事件，并为首次命中的规则创建告警。
 *
 * <p>告警只写 metrics_db 运营投影，不反向修改交易或账户。活动告警存在时不重复开单，运营人员仍须
 * 通过 B 端完成确认、恢复和关闭。</p>
 */
@Service
@ConditionalOnProperty(name = "minialalipay.monitoring.enabled", havingValue = "true", matchIfMissing = true)
public class AlertThresholdEvaluator {
    private final MonitoringProjectionStore store;
    private final MonitoringEventConsumer eventConsumer;
    private final SecurityMaterialPort secure;
    private final Clock clock;
    private final long windowSeconds;

    @Autowired
    public AlertThresholdEvaluator(MonitoringProjectionStore store, MonitoringEventConsumer eventConsumer,
                                   SecurityMaterialPort secure,
                                   @Value("${minialalipay.monitoring.alert.window-seconds:60}") long windowSeconds) {
        this(store, eventConsumer, secure, Clock.systemUTC(), windowSeconds);
    }

    AlertThresholdEvaluator(MonitoringProjectionStore store, MonitoringEventConsumer eventConsumer,
                            SecurityMaterialPort secure, Clock clock, long windowSeconds) {
        if (windowSeconds < 1 || windowSeconds > 3600) throw new IllegalArgumentException("告警窗口必须在 1 到 3600 秒之间");
        this.store = store;
        this.eventConsumer = eventConsumer;
        this.secure = secure;
        this.clock = clock;
        this.windowSeconds = windowSeconds;
    }

    /** 每个窗口重新计算规则；判断和告警投影处于同一监控库事务。 */
    @Scheduled(fixedDelayString = "${minialalipay.monitoring.alert.fixed-delay-ms:10000}",
            initialDelayString = "${minialalipay.monitoring.alert.initial-delay-ms:5000}")
    @Transactional
    public void evaluateRules() {
        Instant end = clock.instant();
        Instant start = end.minusSeconds(windowSeconds);
        for (AlertRule rule : store.listAlertRules()) {
            if (!rule.enabled()) continue;
            long actual = store.countMetric(rule.metricCode(), start, end);
            if (!matches(rule.operator(), actual, rule.thresholdValue())) continue;
            if (store.findActiveAlertByRule(rule.ruleCode()).isPresent()) continue;
            String alertId = secure.stableId("monitor-alert:" + rule.ruleCode() + ":" + end.toEpochMilli() / 86_400_000L);
            Map<String, String> attributes = new HashMap<>();
            attributes.put("alertId", alertId);
            attributes.put("alertType", rule.ruleCode());
            attributes.put("severity", rule.severity());
            attributes.put("status", "OPEN");
            attributes.put("metricCode", rule.metricCode());
            attributes.put("actualValue", String.valueOf(actual));
            attributes.put("thresholdValue", String.valueOf(rule.thresholdValue()));
            attributes.put("reason", rule.metricCode() + "=" + actual + " " + rule.operator() + " " + rule.thresholdValue());
            eventConsumer.consume(new MonitoringEvent(secure.newId(), "alert.status.changed", 1, end,
                    secure.newTraceId(), attributes));
        }
    }

    private static boolean matches(String operator, long actual, long threshold) {
        return switch (operator) {
            case "GT" -> actual > threshold;
            case "GTE" -> actual >= threshold;
            case "LT" -> actual < threshold;
            case "LTE" -> actual <= threshold;
            default -> false;
        };
    }
}
