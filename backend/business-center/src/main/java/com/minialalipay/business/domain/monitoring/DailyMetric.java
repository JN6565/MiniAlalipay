package com.minialalipay.business.domain.monitoring;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 通过质量门禁的 T+1 日指标只读投影。
 *
 * <p>只发布质量状态为 {@code PASSED} 或 {@code WARNING} 的指标；失败或未知质量的指标不进入报表，
 * 避免把未验证的数值交给运营决策。</p>
 */
public record DailyMetric(String metricCode, LocalDate reportDate, long value,
                          String metricVersion, String qualityStatus) {
    /** 创建日指标投影。 */
    public DailyMetric {
        require(metricCode, "指标代码");
        Objects.requireNonNull(reportDate, "报表日期不能为空");
        require(metricVersion, "指标版本");
        require(qualityStatus, "质量状态");
        if (value < 0) throw new IllegalArgumentException("指标值不能为负");
        if (!"PASSED".equals(qualityStatus) && !"WARNING".equals(qualityStatus)) {
            throw new IllegalArgumentException("日指标质量状态只能是 PASSED 或 WARNING");
        }
    }

    private static void require(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
    }
}
