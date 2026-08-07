package com.minialalipay.business.domain.risk;

import java.time.Instant;
import java.util.Objects;

/**
 * 资金受理前的风控决策事实。
 *
 * <p>决策一经生成不可修改。人工复核决策不得关联已创建交易，确保它只作为受理前拦截，
 * 由工单模块记录人工处置过程。</p>
 */
public final class RiskDecision {
    private final String decisionId;
    private final String subjectType;
    private final String subjectId;
    private final String transactionId;
    private final String ruleVersion;
    private final String riskLevel;
    private final RiskDecisionStatus status;
    private final String reasonCode;
    private final Instant createdAt;

    /** 创建允许进入可信确认流程的决策。 */
    public static RiskDecision pass(String decisionId, String subjectType, String subjectId,
                                    String ruleVersion, String riskLevel, Instant now) {
        return new RiskDecision(decisionId, subjectType, subjectId, null, ruleVersion, riskLevel,
                RiskDecisionStatus.PASS, "PASS", now);
    }

    /** 创建拒绝资金受理的决策。 */
    public static RiskDecision reject(String decisionId, String subjectType, String subjectId,
                                      String ruleVersion, String riskLevel, String reasonCode, Instant now) {
        return new RiskDecision(decisionId, subjectType, subjectId, null, ruleVersion, riskLevel,
                RiskDecisionStatus.REJECT, required(reasonCode, "拒绝原因码"), now);
    }

    /** 创建需要运营人员审核且尚未创建交易的决策。 */
    public static RiskDecision manualReview(String decisionId, String subjectType, String subjectId,
                                            String transactionId, String ruleVersion, String riskLevel, Instant now) {
        return new RiskDecision(decisionId, subjectType, subjectId, transactionId, ruleVersion, riskLevel,
                RiskDecisionStatus.MANUAL_REVIEW, "RISK_MANUAL_REVIEW", now);
    }

    /** 从持久化事实重建风控决策。 */
    public RiskDecision(String decisionId, String subjectType, String subjectId, String transactionId,
                        String ruleVersion, String riskLevel, RiskDecisionStatus status,
                        String reasonCode, Instant createdAt) {
        this.decisionId = required(decisionId, "风控决策 ID");
        this.subjectType = required(subjectType, "风控主体类型");
        this.subjectId = required(subjectId, "风控主体 ID");
        this.transactionId = transactionId;
        this.ruleVersion = required(ruleVersion, "风控规则版本");
        this.riskLevel = required(riskLevel, "风险等级");
        this.status = Objects.requireNonNull(status, "风控决策状态不能为空");
        this.reasonCode = reasonCode;
        if (status == RiskDecisionStatus.MANUAL_REVIEW && transactionId != null) {
            throw new IllegalArgumentException("受理前人工复核决策不得关联资金交易");
        }
        if (status == RiskDecisionStatus.REJECT && (reasonCode == null || reasonCode.isBlank())) {
            throw new IllegalArgumentException("风控拒绝必须记录原因码");
        }
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getDecisionId() { return decisionId; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public String getTransactionId() { return transactionId; }
    public String getRuleVersion() { return ruleVersion; }
    public String getRiskLevel() { return riskLevel; }
    public RiskDecisionStatus getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public Instant getCreatedAt() { return createdAt; }
}
