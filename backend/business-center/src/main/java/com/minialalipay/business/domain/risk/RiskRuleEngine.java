package com.minialalipay.business.domain.risk;

/**
 * 受理前风控规则引擎（PRD 9.5 FR-RC-001 基础风控）。
 *
 * <p>纯函数规则评估，不依赖任何端口。只实现业务中心自身可判定、不依赖账户或信用数据的规则：
 * R-02 单笔限额、R-03 高频交易、R-04 大额交易、R-05 新交易对手、R-06 重复特征。
 * 依赖账户中心余额、账户状态与信用额度的 R-01/R-07/R-08/R-09/R-10 由资金内核在 TCC 阶段校验，
 * 本引擎不重复评估，避免业务中心伪造账户或额度事实。</p>
 *
 * <p>评估优先级：拒绝最高，其次转人工复核，再次高风险提示，默认放行。
 * 高风险提示（R-04/R-05/R-06）在 MVP 中不阻断流程，仅落库风险等级供审计和后续前端强提示增强。</p>
 */
public final class RiskRuleEngine {

    /** 当前规则版本，决策表按此版本审计。 */
    public static final String RULE_VERSION = "MVP-20260806";

    /** R-02 单笔限额：金额大于 5 万元（5_000_000 分）拒绝。 */
    private static final long SINGLE_LIMIT_FEN = 5_000_000L;

    /** R-03 高频交易：高频窗口内发起超过 5 笔转人工确认。 */
    private static final int HIGH_FREQUENCY_THRESHOLD = 5;

    /** R-04 大额交易：金额大于等于 5 千元（500_000 分）强风险提示。 */
    private static final long LARGE_AMOUNT_FEN = 500_000L;

    /** R-05 新交易对手：首次向该收款人且金额大于等于 1 千元（100_000 分）强风险提示。 */
    private static final long NEW_PAYEE_AMOUNT_FEN = 100_000L;

    private RiskRuleEngine() { }

    /** 依据订单与历史事实上下文评估风控决策。 */
    public static RiskAssessment assess(RiskContext ctx) {
        if (ctx.amountFen() > SINGLE_LIMIT_FEN) {
            return new RiskAssessment(RiskDecisionStatus.REJECT, "HIGH", "R-02_PAYMENT_AMOUNT_EXCEEDS_LIMIT", RULE_VERSION);
        }
        if (ctx.recentPaymentCount() > HIGH_FREQUENCY_THRESHOLD) {
            return new RiskAssessment(RiskDecisionStatus.MANUAL_REVIEW, "HIGH", "R-03_HIGH_FREQUENCY_TRADING", RULE_VERSION);
        }
        if (ctx.amountFen() >= LARGE_AMOUNT_FEN) {
            return new RiskAssessment(RiskDecisionStatus.PASS, "HIGH", "R-04_LARGE_AMOUNT", RULE_VERSION);
        }
        if (ctx.repeatedPaymentCount() > 0) {
            return new RiskAssessment(RiskDecisionStatus.PASS, "HIGH", "R-06_REPEATED_PAYMENT_FEATURE", RULE_VERSION);
        }
        if (!ctx.hasTradedWith() && ctx.amountFen() >= NEW_PAYEE_AMOUNT_FEN) {
            return new RiskAssessment(RiskDecisionStatus.PASS, "HIGH", "R-05_NEW_PAYEE", RULE_VERSION);
        }
        return new RiskAssessment(RiskDecisionStatus.PASS, "LOW", null, RULE_VERSION);
    }
}
