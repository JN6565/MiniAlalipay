package com.minialalipay.business.domain.monitoring;

import java.time.Instant;
import java.util.Objects;

/**
 * 数据质量检查的不可变只读投影。
 *
 * <p>用于完整性、唯一性、及时性和一致性等运营展示。它只记录检查事实，不能修改业务订单或资金状态。
 * {@code taskCode} 为检查任务编码（如“交易完整性”），{@code ruleCode} 为该任务下的规则编码，
 * 运营端据二者生成级联筛选，不必猜测编码。</p>
 */
public record DataQualityResult(String resultId, String taskCode, String ruleCode, String status,
                                long checkedCount, long failedCount, Instant completedAt) {
    /** 创建已完成的数据质量检查结果。 */
    public DataQualityResult {
        require(resultId, "数据质量结果 ID");
        require(taskCode, "任务编码");
        require(ruleCode, "规则编码");
        require(status, "检查状态");
        if (checkedCount < 0 || failedCount < 0 || failedCount > checkedCount) {
            throw new IllegalArgumentException("数据质量检查数量不合法");
        }
        Objects.requireNonNull(completedAt, "完成时间不能为空");
    }

    private static void require(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
    }
}
