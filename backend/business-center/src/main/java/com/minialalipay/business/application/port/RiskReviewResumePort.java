package com.minialalipay.business.application.port;

import java.time.Instant;

/**
 * 人工复核批准后的来源订单恢复端口。
 *
 * <p>运营批准 {@code RISK_PRECHECK} 工单后，业务中心将来源订单从 {@code RISK_REVIEW} 恢复为待确认，
 * 使用户可以重新确认。实现只更新来源订单状态，不改变资金交易或工单终态。</p>
 */
public interface RiskReviewResumePort {
    /**
     * 恢复来源订单到待确认状态。
     *
     * @param subjectType 业务主体类型，与确认令牌的 {@link com.minialalipay.business.domain.confirmation.SubjectType} 一致
     * @param subjectId 业务主体 ID
     * @param now 操作时间
     * @return 主体不存在或不在人工复核状态时视为已处理返回 true
     */
    boolean resumeToConfirmation(String subjectType, String subjectId, Instant now);
}
