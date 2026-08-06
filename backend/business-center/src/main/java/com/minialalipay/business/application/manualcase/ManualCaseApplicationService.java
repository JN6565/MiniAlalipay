package com.minialalipay.business.application.manualcase;

import com.minialalipay.business.application.port.ManualCaseStore;
import com.minialalipay.business.application.port.RiskReviewResumePort;
import com.minialalipay.business.domain.manualcase.ManualCase;
import com.minialalipay.business.domain.manualcase.ManualCaseStatus;
import com.minialalipay.business.domain.manualcase.ManualCaseType;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

/**
 * 人工工单查询和处置应用服务。
 *
 * <p>处置仅更新工单自身的操作人、理由和证据，不调用统一交易、TCC 或账户端口。任何资金恢复仍由既有恢复流程
 * 基于权威资金事实执行，不能由本服务审批为成功。</p>
 */
@Service
public class ManualCaseApplicationService {
    private final ManualCaseStore store;
    private final SecurityMaterialPort secure;
    private final RiskReviewResumePort resumePort;
    private final Clock clock;

    /** 创建工单应用服务。 */
    @Autowired
    public ManualCaseApplicationService(ManualCaseStore store, SecurityMaterialPort secure, RiskReviewResumePort resumePort) {
        this(store, secure, resumePort, Clock.systemUTC());
    }

    /** 供无恢复依赖的切片测试构造；生产环境必须使用完整构造器。 */
    public ManualCaseApplicationService(ManualCaseStore store, SecurityMaterialPort secure) {
        this(store, secure, null, Clock.systemUTC());
    }

    ManualCaseApplicationService(ManualCaseStore store, SecurityMaterialPort secure, Clock clock) {
        this(store, secure, null, clock);
    }

    ManualCaseApplicationService(ManualCaseStore store, SecurityMaterialPort secure, RiskReviewResumePort resumePort, Clock clock) {
        this.store = store;
        this.secure = secure;
        this.resumePort = resumePort;
        this.clock = clock;
    }

    /** 查询运营可见工单；status/type 为空表示不按该维度过滤。 */
    @Transactional(readOnly = true)
    public List<ManualCase> list(String cursor, ManualCaseStatus status, ManualCaseType type, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("工单分页数量必须在 1 到 100 之间");
        return store.list(cursor, status, type, limit);
    }

    /**
     * 创建待领取的人工工单（前置风控或事务恢复）。
     *
     * <p>同一主体已有开放或已领取活动工单时幂等返回 {@code null}，不重复建单；本方法只记录工单，
     * 不改变资金交易或订单终态。</p>
     *
     * @param type 工单类型（前置风控预检或事务恢复）
     * @param subjectType 业务主体类型
     * @param subjectId 业务主体 ID
     * @param reasonCode 触发原因码
     * @return 新建工单；主体已有活动工单时返回 {@code null}
     */
    @Transactional
    public ManualCase createCase(ManualCaseType type, String subjectType, String subjectId, String reasonCode) {
        ManualCase manualCase = ManualCase.open(secure.newId(), type, subjectType, subjectId, reasonCode, clock.instant());
        return store.create(manualCase) ? manualCase : null;
    }

    /** 执行领取、解决、重开或关闭工单的状态机动作。 */
    @Transactional
    public ManualCase decide(String operatorId, String caseId, Decision decision, long version,
                             String reason, String evidence, String idempotencyKey) {
        byte[] requestHash = secure.digest(canonicalDecision(caseId, decision, version, reason, evidence));
        ManualCaseStore.DecisionIdempotencyRecord existing = store
                .findDecisionIdempotency(operatorId, idempotencyKey).orElse(null);
        if (existing != null) return replay(existing, requestHash);

        if (!store.reserveDecisionIdempotency(secure.newId(), operatorId, idempotencyKey, requestHash)) {
            return replay(store.findDecisionIdempotency(operatorId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("工单幂等占位冲突后未读取到既有记录")), requestHash);
        }
        ManualCase manualCase = store.find(caseId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
        try {
            switch (decision) {
                case CLAIM -> manualCase.claim(operatorId, version, clock.instant());
                case RESOLVE -> manualCase.resolve(operatorId, version, reason, evidence, clock.instant());
                case REOPEN -> manualCase.reopen(operatorId, version, reason, clock.instant());
                case CLOSE -> manualCase.close(operatorId, version, reason, evidence, clock.instant());
            }
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(BusinessErrorCode.EVIDENCE_REQUIRED);
        } catch (IllegalStateException invalid) {
            if (invalid.getMessage().contains("版本")) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
            throw new BusinessException(BusinessErrorCode.CASE_STATE_INVALID);
        }
        if (!store.update(manualCase, version)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        store.completeDecisionIdempotency(operatorId, idempotencyKey, manualCase);
        // 前置风控工单批准后，将来源订单从 RISK_REVIEW 恢复为待确认，用户可重新发起确认。
        if (resumePort != null && decision == Decision.RESOLVE
                && manualCase.getType() == ManualCaseType.RISK_PRECHECK) {
            resumePort.resumeToConfirmation(manualCase.getSubjectType(), manualCase.getSubjectId(), clock.instant());
        }
        return manualCase;
    }

    private ManualCase replay(ManualCaseStore.DecisionIdempotencyRecord existing, byte[] requestHash) {
        if (!Arrays.equals(existing.requestHash(), requestHash)) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        }
        if (existing.result() == null) {
            throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        }
        return existing.result();
    }

    private static String canonicalDecision(String caseId, Decision decision, long version, String reason, String evidence) {
        return caseId + "\n" + decision.name() + "\n" + version + "\n"
                + valueOrEmpty(reason) + "\n" + valueOrEmpty(evidence);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 工单处置命令类型，与 OpenAPI 的 decision 枚举对应。 */
    public enum Decision {
        /** 领取开放工单。 */
        CLAIM,
        /** 记录有证据支持的解决结果。 */
        RESOLVE,
        /** 新证据出现时重新打开已解决工单。 */
        REOPEN,
        /** 以最终证据关闭工单。 */
        CLOSE
    }
}
