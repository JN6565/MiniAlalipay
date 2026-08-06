package com.minialalipay.business.domain.manualcase;

import java.time.Instant;
import java.util.Objects;

/**
 * 风险复核或交易恢复的人工工单聚合。
 *
 * <p>工单只记录运营处置，不能直接改变资金交易终态。每个处置动作要求操作者、理由、证据和 CAS 版本，
 * 以支持审计和重复请求防护。</p>
 */
public final class ManualCase {
    private final String caseId;
    private final ManualCaseType type;
    private final String subjectType;
    private final String subjectId;
    private final String reasonCode;
    private ManualCaseStatus status;
    private String operatorId;
    private String lastReason;
    private String evidenceReference;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /** 创建等待领取的人工工单。 */
    public static ManualCase open(String caseId, ManualCaseType type, String subjectType, String subjectId,
                                  String reasonCode, Instant now) {
        return new ManualCase(caseId, type, subjectType, subjectId, reasonCode, ManualCaseStatus.OPEN,
                null, null, null, 0L, now, now);
    }

    /** 从持久化事实重建人工工单。 */
    public ManualCase(String caseId, ManualCaseType type, String subjectType, String subjectId, String reasonCode,
                      ManualCaseStatus status, String operatorId, String lastReason, String evidenceReference,
                      long version, Instant createdAt, Instant updatedAt) {
        this.caseId = required(caseId, "工单 ID");
        this.type = Objects.requireNonNull(type, "工单类型不能为空");
        this.subjectType = required(subjectType, "工单主体类型");
        this.subjectId = required(subjectId, "工单主体 ID");
        this.reasonCode = required(reasonCode, "工单原因码");
        this.status = Objects.requireNonNull(status, "工单状态不能为空");
        this.operatorId = operatorId;
        this.lastReason = lastReason;
        this.evidenceReference = evidenceReference;
        if (version < 0) throw new IllegalArgumentException("工单版本不得为负数");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /** 领取开放工单；同一领取人重试保持幂等。 */
    public void claim(String actorId, long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        String validActorId = required(actorId, "操作人 ID");
        if (status == ManualCaseStatus.CLAIMED && validActorId.equals(operatorId)) return;
        if (status != ManualCaseStatus.OPEN) throw new IllegalStateException("工单当前不可领取");
        operatorId = validActorId;
        status = ManualCaseStatus.CLAIMED;
        advance(now);
    }

    /** 记录有证据支持的处置结果。 */
    public void resolve(String actorId, long expectedVersion, String reason, String evidence, Instant now) {
        checkOperatorAndStatus(actorId, expectedVersion, ManualCaseStatus.CLAIMED);
        lastReason = required(reason, "处置理由");
        evidenceReference = required(evidence, "处置证据");
        status = ManualCaseStatus.RESOLVED;
        advance(now);
    }

    /** 发现新证据时重新打开已处理工单。 */
    public void reopen(String actorId, long expectedVersion, String reason, Instant now) {
        checkOperatorAndStatus(actorId, expectedVersion, ManualCaseStatus.RESOLVED);
        lastReason = required(reason, "重开理由");
        status = ManualCaseStatus.CLAIMED;
        advance(now);
    }

    /** 以最终理由和证据关闭已处理工单。 */
    public void close(String actorId, long expectedVersion, String reason, String evidence, Instant now) {
        checkOperatorAndStatus(actorId, expectedVersion, ManualCaseStatus.CLAIMED, ManualCaseStatus.RESOLVED);
        lastReason = required(reason, "关闭理由");
        evidenceReference = required(evidence, "关闭证据");
        status = ManualCaseStatus.CLOSED;
        advance(now);
    }

    private void checkOperatorAndStatus(String actorId, long expectedVersion, ManualCaseStatus... expectedStatuses) {
        checkVersion(expectedVersion);
        if (!Objects.equals(operatorId, actorId)) throw new IllegalStateException("仅工单领取人可以处置");
        for (ManualCaseStatus expectedStatus : expectedStatuses) if (status == expectedStatus) return;
        throw new IllegalStateException("工单当前不可处置");
    }

    private void checkVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("工单版本已经变化");
    }

    private void advance(Instant now) { version++; updatedAt = now; }
    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getCaseId() { return caseId; }
    public ManualCaseType getType() { return type; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public String getReasonCode() { return reasonCode; }
    public ManualCaseStatus getStatus() { return status; }
    public String getOperatorId() { return operatorId; }
    public String getLastReason() { return lastReason; }
    public String getEvidenceReference() { return evidenceReference; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
