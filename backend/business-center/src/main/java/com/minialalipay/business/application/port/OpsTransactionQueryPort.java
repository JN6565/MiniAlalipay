package com.minialalipay.business.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * B 端运营交易查询端口。
 *
 * <p>只读投影 {@code business_db} 中业务中心拥有的资金交易事实（统一交易、TCC 全局、Outbox 终态事件），
 * 供管理员与运营人员查看全平台脱敏交易与链路追溯；禁止通过本端口修改余额、账本或交易状态。</p>
 */
public interface OpsTransactionQueryPort {
    /** 按游标分页查询脱敏交易摘要；status/businessType 为空表示不限，cursor 为空表示从最新开始。 */
    List<OpsTransactionRow> listTransactionsForOps(OpsTransactionQuery query);

    /** 查询单笔脱敏交易详情及关联的 TCC 全局、最新 Outbox 事件和活动人工工单；不存在返回空。 */
    Optional<OpsTransactionDetail> findTransactionForOps(String transactionId);

    /** 按链路编号查询跨服务脱敏链路片段（业务中心、账户账本、用户审计、AI 工具/审计）；traceId 无结果返回空列表。 */
    List<TraceSpan> findTraceSpansByTraceId(String traceId);

    /** 运营交易查询条件；cursor 为稳定交易 ID 游标，时间范围为创建时间过滤。 */
    record OpsTransactionQuery(String status, String businessType, String cursor, int limit,
                               Instant from, Instant to) { }

    /** B 端脱敏交易摘要；不暴露完整用户或账户标识，金额使用整数分。 */
    record OpsTransactionRow(String transactionId, String businessType, String sourceType, String sourceOrderId,
                             String initiatorMasked, long amountFen, String status, String riskLevel, String traceId,
                             Instant createdAt, Instant updatedAt) { }

    /** B 端单笔交易详情：脱敏摘要 + TCC 全局状态 + 最新 Outbox 事件 + 活动人工工单。 */
    record OpsTransactionDetail(OpsTransactionRow row, String fundingSource, String tccStatus, int tccRetryCount,
                                String latestOutboxEventType, String outboxStatus, String activeManualCaseId) { }

    /** 链路追溯片段；service 表示归属服务，operation 表示该服务内可核验的事实阶段，transactionId 为可空交易归属。 */
    record TraceSpan(String service, String operation, String status, String detail, String traceId,
                     Instant occurredAt, String transactionId) { }
}
