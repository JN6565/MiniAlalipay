package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.manualcase.ManualCase;

import java.util.List;
import java.util.Optional;

/**
 * 人工工单的 business_db 仓储端口。
 *
 * <p>实现仅持久化工单及其审计性处置，不得通过本端口修改资金交易、账户、冻结或账本事实。</p>
 */
public interface ManualCaseStore {
    /** 查询运营可见工单，游标由基础设施实现为稳定不透明值。 */
    List<ManualCase> list(String cursor, int limit);

    /** 按 ID 查询工单。 */
    Optional<ManualCase> find(String caseId);

    /**
     * 在同一业务库事务中创建开放工单。
     *
     * <p>主体已有开放或已领取活动工单（由 {@code active_subject_key} 唯一键保证）时返回 false，
     * 调用方应视为该主体已处于人工处置中。</p>
     */
    boolean create(ManualCase manualCase);

    /** 按旧版本 CAS 持久化工单处置；影响行数为零时返回 false。 */
    boolean update(ManualCase manualCase, long expectedVersion);

    /** 查询同一运营人员已完成的工单处置幂等结果。 */
    Optional<DecisionIdempotencyRecord> findDecisionIdempotency(String operatorId, String idempotencyKey);

    /**
     * 抢占工单处置幂等键。
     *
     * <p>必须与工单 CAS 更新、幂等结果快照在同一 {@code business_db} 本地事务中提交，确保进程崩溃或
     * 重复投递不会把同一处置执行两次。</p>
     */
    boolean reserveDecisionIdempotency(String recordId, String operatorId, String idempotencyKey, byte[] requestHash);

    /** 保存已完成处置的响应快照，供同键同参请求稳定回放。 */
    void completeDecisionIdempotency(String operatorId, String idempotencyKey, ManualCase manualCase);

    /** 工单处置幂等事实；{@code result} 为 null 表示正在由同一事务执行，调用方应安全重试。 */
    record DecisionIdempotencyRecord(byte[] requestHash, ManualCase result) { }
}
