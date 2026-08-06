package com.minialalipay.business.domain.monitoring;

import java.time.Instant;

/**
 * 告警规则及阈值配置。
 *
 * <p>描述监控告警的触发条件（指标代码、比较算子和阈值）与严重级别。阈值由管理员通过运营接口在版本 CAS
 * 下修改，属于运营投影，不持有任何资金事实。</p>
 */
public record AlertRule(String ruleCode, String ruleName, String metricCode, String severity,
                        String operator, long thresholdValue, boolean enabled, long version,
                        String updatedBy, Instant updatedAt) {
    /** 创建告警规则。 */
    public AlertRule {
        require(ruleCode, "规则代码");
        require(ruleName, "规则名称");
        require(metricCode, "指标代码");
        require(severity, "严重级别");
        require(operator, "比较算子");
        if (thresholdValue < 0) throw new IllegalArgumentException("告警阈值不能为负数");
    }

    /**
     * 校验阈值更新并返回新版本规则；只允许调整阈值，规则结构保持不可变。
     *
     * @param newThresholdValue 新阈值
     * @param operatorId 更新操作者，用于审计
     * @param now 更新时间
     */
    public AlertRule withThreshold(long newThresholdValue, String operatorId, Instant now) {
        if (newThresholdValue < 0) throw new IllegalArgumentException("告警阈值不能为负数");
        if (operatorId == null || operatorId.isBlank()) throw new IllegalArgumentException("更新操作者不能为空");
        return new AlertRule(ruleCode, ruleName, metricCode, severity, operator, newThresholdValue,
                enabled, version + 1, operatorId, now);
    }

    private static void require(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
    }
}
