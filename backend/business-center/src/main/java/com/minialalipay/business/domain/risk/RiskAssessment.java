package com.minialalipay.business.domain.risk;

/**
 * 规则引擎评估结果，作为落库决策与调用方联动的依据。
 *
 * <p>REJECT 与 MANUAL_REVIEW 由调用方拦截或转人工；PASS 无论风险等级高低均放行，
 * 高风险等级（HIGH）仅作为强风险提示与审计事实保留。</p>
 *
 * @param status 风控决策状态
 * @param riskLevel 风险等级 LOW/MEDIUM/HIGH，与 OpenAPI 风险等级口径一致
 * @param reasonCode 命中的规则标识或拒绝原因码；默认放行时为空
 * @param ruleVersion 规则版本，用于决策表审计
 */
public record RiskAssessment(RiskDecisionStatus status, String riskLevel, String reasonCode, String ruleVersion) {
}
