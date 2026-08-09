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

    /**
     * 按上海业务日的时间边界汇总看板交易指标。
     *
     * <p>成功率分母只包括 SUCCESS、REVERSED、CANCELLED 三种确定终态；PROCESSING、COMPENSATING、
     * MANUAL_REVIEW 不属于成功或失败，防止未收敛交易污染经营指标。</p>
     */
    default DashboardTransactionStats dashboardTransactionStats(Instant from, Instant to) {
        return new DashboardTransactionStats(0L, 0L, 0L, 0L);
    }


    /**
     * 运营交易查询条件；cursor 为不透明复合游标（创建时间 + 交易 ID），时间范围为创建时间过滤，
     * initiator 为发起用户 ID 关键词（按原始发起人 ID 模糊匹配，空表示不限）。
     */
    record OpsTransactionQuery(String status, String businessType, String initiator, String cursor, int limit,
                               Instant from, Instant to) { }

    /** 看板交易汇总；金额单位为分，成功率单位为万分比。 */
    record DashboardTransactionStats(long successAmountFen, long successRateBps, long pendingManualCaseCount,
                                     long definitiveTransactionCount) { }


    /** 游标键：创建时间毫秒与交易 ID 复合键，保证「创建时间倒序」分页稳定并处理同毫秒平局。 */
    record CursorKey(Instant createdAt, String transactionId) { }

    /**
     * 编码下一页游标：{@code epochMillis:transactionId}。
     *
     * <p>交易 ID 为随机 base32 非时间有序，因此排序以创建时间倒序为准、交易 ID 作平局裁决；
     * 游标必须携带创建时间才能继续「比边界行更旧」的比较，而不能只带交易 ID。</p>
     */
    static String encodeCursor(Instant createdAt, String transactionId) {
        return createdAt.toEpochMilli() + ":" + transactionId;
    }

    /** 解析不透明游标；格式非法或为空时返回空，调用方等价于从未携带游标（从最新开始）。 */
    static Optional<CursorKey> parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return Optional.empty();
        int sep = cursor.indexOf(':');
        if (sep <= 0 || sep == cursor.length() - 1) return Optional.empty();
        try {
            Instant createdAt = Instant.ofEpochMilli(Long.parseLong(cursor.substring(0, sep)));
            return Optional.of(new CursorKey(createdAt, cursor.substring(sep + 1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

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
