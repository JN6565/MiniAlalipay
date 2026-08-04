package com.minialalipay.account.domain.reconciliation;

import java.time.Instant;

/**
 * 对账差异仓储；只追加差异证据，不允许直接修改余额、冻结或历史凭证。
 */
public interface ReconciliationDiffRepository {
    /**
     * 幂等追加一条交易差异。
     *
     * @param diffId 差异 ID
     * @param transactionId 统一资金交易 ID
     * @param diffType 差异类型
     * @param expectedJson 预期事实 JSON
     * @param actualJson 实际事实 JSON
     * @param manualCaseId 关联的人工工单 ID
     * @param traceId 链路 ID
     * @param detectedAt 检测时间
     */
    void append(String diffId, String transactionId, String diffType, String expectedJson,
                String actualJson, String manualCaseId, String traceId, Instant detectedAt);
}
