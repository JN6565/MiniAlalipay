package com.minialalipay.business.domain.monitoring;

import java.time.Instant;
import java.util.Objects;

/**
 * 分钟级实时指标的不可变只读投影。
 *
 * <p>由监控事件投影产生，仅用于运营展示，不能反向修改交易或资金状态。质量状态只允许
 * {@code PASSED} 或 {@code WARNING}；当投影质量不满足时不向观察者发布指标数值。</p>
 */
public record RealtimeMetric(String metricCode, Instant bucketAt, long value,
                             String metricVersion, String qualityStatus) {
    /** 创建实时指标投影。 */
    public RealtimeMetric {
        require(metricCode, "指标代码");
        Objects.requireNonNull(bucketAt, "指标时间桶不能为空");
        require(metricVersion, "指标版本");
        require(qualityStatus, "质量状态");
        if (value < 0) throw new IllegalArgumentException("指标值不能为负");
        if (!"PASSED".equals(qualityStatus) && !"WARNING".equals(qualityStatus)) {
            throw new IllegalArgumentException("实时指标质量状态只能是 PASSED 或 WARNING");
        }
    }

    private static void require(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
    }
}
