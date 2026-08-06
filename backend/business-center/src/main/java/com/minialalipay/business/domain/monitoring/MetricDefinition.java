package com.minialalipay.business.domain.monitoring;

import java.util.Objects;

/**
 * 运营指标的只读口径定义。
 *
 * <p>只展示处于激活状态的指标版本，运营据此解释报表和实时指标的含义；定义本身不持有任何资金事实。</p>
 */
public record MetricDefinition(String metricCode, String version, String name, String unit, String formula) {
    /** 创建指标口径定义。 */
    public MetricDefinition {
        require(metricCode, "指标代码");
        require(version, "指标版本");
        require(name, "指标名称");
        require(unit, "指标单位");
        require(formula, "指标口径");
    }

    private static void require(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
    }
}
