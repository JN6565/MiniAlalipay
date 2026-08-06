package com.minialalipay.business.application.risk;

import com.minialalipay.business.application.port.RiskDecisionStore;
import com.minialalipay.business.application.port.RiskHistoryPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.risk.RiskAssessment;
import com.minialalipay.business.domain.risk.RiskContext;
import com.minialalipay.business.domain.risk.RiskDecision;
import com.minialalipay.business.domain.risk.RiskDecisionStatus;
import com.minialalipay.business.domain.risk.RiskRuleEngine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * 资金受理前的风控预检应用服务。
 *
 * <p>每次预检先按统一交易投影汇总付款人历史（高频、重复特征、是否新对手），
 * 由规则引擎产出决策，并将决策落库到 {@code risk_decision} 供审计与后续读取，
 * 再向调用方返回联动裁决。决策是受理前拦截，不能关联已创建的资金交易。</p>
 */
@Service
public class RiskEvaluationService {
    /** R-03 高频交易窗口：1 分钟。 */
    private static final Duration HIGH_FREQUENCY_WINDOW = Duration.ofMinutes(1);
    /** R-06 重复特征窗口：30 秒。 */
    private static final Duration REPEAT_WINDOW = Duration.ofSeconds(30);

    private final RiskDecisionStore store;
    private final RiskHistoryPort history;
    private final SecurityMaterialPort security;

    /** 创建风控预检服务。 */
    public RiskEvaluationService(RiskDecisionStore store, RiskHistoryPort history, SecurityMaterialPort security) {
        this.store = store;
        this.history = history;
        this.security = security;
    }

    /**
     * 评估主体是否可以进入可信确认流程。
     *
     * <p>规则评估后无论结果如何都会落库一条决策事实；拒绝与转人工由调用方按返回值联动。</p>
     *
     * @param subjectType 业务主体类型，与确认令牌的 {@link com.minialalipay.business.domain.confirmation.SubjectType} 一致
     * @param subjectId 业务主体 ID
     * @param payerUserId 付款人用户标识
     * @param payeeAccountId 收款人账户标识
     * @param amountFen 本次支付金额，单位分
     * @param now 评估时间基准
     * @return 风控裁决；由调用方决定放行、拒绝或转人工
     */
    public RiskVerdict evaluatePrecheck(String subjectType, String subjectId, String payerUserId,
                                        String payeeAccountId, long amountFen, Instant now) {
        int recent = history.countRecentPayments(payerUserId, now.minus(HIGH_FREQUENCY_WINDOW));
        int repeated = history.countRepeatedPayments(payerUserId, payeeAccountId, amountFen, now.minus(REPEAT_WINDOW));
        boolean traded = history.hasTradedWith(payerUserId, payeeAccountId);
        RiskAssessment assessment = RiskRuleEngine.assess(new RiskContext(subjectType, subjectId, payerUserId,
                payeeAccountId, amountFen, recent, repeated, traded, now));
        store.save(toDecision(subjectType, subjectId, assessment, now));
        return new RiskVerdict(assessment.status(), assessment.reasonCode(), assessment.riskLevel());
    }

    /** 把规则评估结果转换为不可变风控决策事实。 */
    private RiskDecision toDecision(String subjectType, String subjectId, RiskAssessment assessment, Instant now) {
        return switch (assessment.status()) {
            case REJECT -> RiskDecision.reject(security.newId(), subjectType, subjectId,
                    assessment.ruleVersion(), assessment.riskLevel(), assessment.reasonCode(), now);
            case MANUAL_REVIEW -> RiskDecision.manualReview(security.newId(), subjectType, subjectId,
                    null, assessment.ruleVersion(), assessment.riskLevel(), now);
            case PASS -> RiskDecision.pass(security.newId(), subjectType, subjectId,
                    assessment.ruleVersion(), assessment.riskLevel(), now);
        };
    }

    /** 风控裁决结果；reasonCode 仅在拒绝或人工复核时提供，riskLevel 与 OpenAPI 风险等级口径一致。 */
    public record RiskVerdict(RiskDecisionStatus status, String reasonCode, String riskLevel) { }
}
