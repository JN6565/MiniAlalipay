package com.minialalipay.business.application.collection;

import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.CollectionStore;
import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.application.risk.RiskEvaluationService;
import com.minialalipay.business.application.risk.RiskReviewRouter;
import com.minialalipay.business.domain.collection.CollectionOrder;
import com.minialalipay.business.domain.collection.CollectionOrderEvent;
import com.minialalipay.business.domain.confirmation.Confirmation;
import com.minialalipay.business.domain.confirmation.ConfirmationStatus;
import com.minialalipay.business.domain.confirmation.SubjectType;
import com.minialalipay.business.domain.risk.RiskDecisionStatus;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * C2C 确认和付款应用服务。
 *
 * <p>本服务只负责确认快照、统一资金交易受理和 TCC 启动；余额、冻结与账本只能由账户中心的 TCC 分支处理。</p>
 */
@Service
public class CollectionPaymentApplicationService {
    private final CollectionStore collections;
    private final BusinessStore business;
    private final PaymentProofPort proofs;
    private final SecurityMaterialPort security;
    private final TccCoordinatorPort coordinator;
    private final IdempotencyKeyValidator keys;
    private final RiskEvaluationService risk;
    private final RiskReviewRouter riskRouter;
    private final Clock clock;

    /** 创建 C2C 付款应用服务。 */
    @Autowired
    public CollectionPaymentApplicationService(CollectionStore collections, BusinessStore business, PaymentProofPort proofs,
                                               SecurityMaterialPort security, TccCoordinatorPort coordinator,
                                               IdempotencyKeyValidator keys,
                                               RiskEvaluationService risk, RiskReviewRouter riskRouter) {
        this(collections, business, proofs, security, coordinator, keys, risk, riskRouter, Clock.systemUTC());
    }

    CollectionPaymentApplicationService(CollectionStore collections, BusinessStore business, PaymentProofPort proofs,
                                        SecurityMaterialPort security, TccCoordinatorPort coordinator,
                                        IdempotencyKeyValidator keys, RiskEvaluationService risk,
                                        RiskReviewRouter riskRouter, Clock clock) {
        this.collections = collections;
        this.business = business;
        this.proofs = proofs;
        this.security = security;
        this.coordinator = coordinator;
        this.keys = keys;
        this.risk = risk;
        this.riskRouter = riskRouter;
        this.clock = clock;
    }

    /**
     * 校验支付证明并签发绑定用户所选余额或 Mini 花呗的确认令牌。
     *
     * @param userId 服务端认证的付款人
     * @param orderId C2C 来源订单
     * @param sessionId 绑定 H5 会话
     * @param version 客户端读取到的订单版本
     * @param paymentProof 用户中心签发的一次性支付证明
     * @param fundingSource 用户明确选择的资金来源
     * @return 原始确认令牌，仅该响应可见
     */
    @Transactional
    public IssuedConfirmation issueConfirmation(String userId, String orderId, String sessionId, long version,
                                                String paymentProof, FundingSource fundingSource) {
        if (fundingSource == null) throw new BusinessException(BusinessErrorCode.FUNDING_SOURCE_NOT_ALLOWED);
        CollectionOrder order = payerOrder(userId, orderId, sessionId);
        if (order.getAmountFen() == null || order.getAmountFen() < 1) {
            throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getVersion() != version) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        routeOnRiskVerdict(subjectType(order).name(), order, version);
        PaymentProofPort.VerifiedProof proof = proofs.verify(userId, paymentProof, "COLLECTION_CONFIRM");
        String rawToken = security.newConfirmationToken();
        Instant now = clock.instant();
        Confirmation confirmation = Confirmation.issue(security.newId(), security.digest(rawToken), subjectType(order), orderId,
                subjectHash(order, fundingSource), userId, proof.paymentProofId(), proof.payPasswordVersion(), now);
        business.replaceCollectionConfirmation(confirmation);
        return new IssuedConfirmation(rawToken, "sha256:" + java.util.HexFormat.of().formatHex(subjectHash(order, fundingSource)), confirmation.getExpiresAt());
    }

    /**
     * 签发确认令牌前的风控预检。
     *
     * <p>风控拒绝时直接拦截且不创建资金交易；命中转人工规则时由独立事务把订单置为 {@code RISK_REVIEW}
     * 并创建预检工单，再以 202 告知客户端已进入人工审核。未配置风控评估（切片测试）时默认放行。
     * 评估依据订单中的付款人、收款账户与金额，调用风控规则引擎并落库决策。</p>
     */
    private void routeOnRiskVerdict(String subjectType, CollectionOrder order, long expectedVersion) {
        if (risk == null) return;
        RiskEvaluationService.RiskVerdict verdict = risk.evaluatePrecheck(subjectType, order.getOrderId(),
                order.getPayerUserId(), order.getPayeeAccountId(), order.getAmountFen(), clock.instant());
        if (verdict.status() == RiskDecisionStatus.REJECT) {
            throw new BusinessException(BusinessErrorCode.RISK_REJECTED);
        }
        if (verdict.status() == RiskDecisionStatus.MANUAL_REVIEW) {
            riskRouter.routeCollectionOrderToReview(order, expectedVersion, subjectType, verdict.reasonCode(), clock.instant());
            throw new BusinessException(BusinessErrorCode.RISK_MANUAL_REVIEW);
        }
    }

    /**
     * 消费确认令牌并受理唯一的 TRANSFER 主单。
     *
     * <p>同一来源订单由数据库唯一键收敛为至多一笔交易；返回 PROCESSING 仅代表已受理，不代表资金成功。</p>
     */
    @Transactional
    public FundTransaction pay(String userId, String orderId, String sessionId, String confirmationToken,
                               String idempotencyKey, String traceId) {
        if (!keys.isValid(idempotencyKey)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        CollectionOrder order = payerOrder(userId, orderId, sessionId);
        byte[] tokenDigest = security.digest(confirmationToken);
        byte[] requestHash = security.digest(orderId + "\n" + Base64.getEncoder().encodeToString(tokenDigest));
        Confirmation confirmation = business.findConfirmationForUpdate(tokenDigest)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.CONFIRMATION_MISMATCH));
        FundingSource fundingSource = resolveFundingSource(order, confirmation);
        TransactionType transactionType = fundingSource == FundingSource.MINI_CREDIT
                ? TransactionType.CREDIT_PAY : TransactionType.TRANSFER;
        var repeated = business.findByIdempotency(userId, transactionType, idempotencyKey).orElse(null);
        if (repeated != null) {
            if (!Arrays.equals(repeated.requestHash(), requestHash)) throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
            return repeated.transaction();
        }
        SourceType sourceType = sourceType(order);
        var sameSource = business.findBySource(sourceType.name(), orderId).orElse(null);
        if (sameSource != null) return sameSource.transaction();
        if (confirmation.getSubjectType() != subjectType(order) || !confirmation.getSubjectId().equals(orderId)
                || !confirmation.getPayerUserId().equals(userId) || fundingSource == null) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_STALE);
        }
        if (proofs.currentPayPasswordVersion(userId) != confirmation.getPayPasswordVersion()) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_STALE);
        }
        Instant now = clock.instant();
        try { confirmation.consume(now); }
        catch (IllegalStateException invalid) {
            throw new BusinessException(confirmation.getStatus() == ConfirmationStatus.EXPIRED
                    ? BusinessErrorCode.CONFIRMATION_EXPIRED : BusinessErrorCode.CONFIRMATION_MISMATCH);
        }
        if (!business.updateConfirmation(confirmation, ConfirmationStatus.ACTIVE.name())) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_MISMATCH);
        }
        long expectedVersion = order.getVersion();
        String transactionId = security.newId();
        try { order.acceptByFundTransaction(expectedVersion, transactionId, now); }
        catch (IllegalStateException invalid) { throw new BusinessException(BusinessErrorCode.ORDER_STATE_INVALID); }
        CollectionOrderEvent processingEvent = order.getRequestId() == null ? null : new CollectionOrderEvent(
                security.newId(), order.getRequestId(), order.getOrderId(), transactionId, "PROCESSING", now);
        if (!collections.acceptOrderForPayment(order, expectedVersion, processingEvent)) {
            throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        }
        FundTransaction transaction = FundTransaction.accept(transactionId, transactionType, sourceType, orderId,
                userId, order.getPayerAccountId(), order.getPayeeAccountId(), fundingSource, order.getAmountFen(),
                idempotencyKey, "LOW", validTraceId(traceId), now);
        business.createTransaction(transaction, requestHash, security.newId(), now);
        afterCommit(() -> coordinator.startOrResume(transaction));
        return transaction;
    }

    /** 读取订单并验证付款人和 H5 会话两层对象授权。 */
    @Transactional(readOnly = true)
    public CollectionOrder getForPayer(String userId, String orderId, String sessionId) { return payerOrder(userId, orderId, sessionId); }

    private CollectionOrder payerOrder(String userId, String orderId, String sessionId) {
        CollectionOrder order = collections.findOrder(orderId).orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
        boolean bound = collections.findOrderByBootstrapSessionId(security.stableId(sessionId))
                .map(value -> value.getOrderId().equals(orderId)).orElse(false);
        if (!bound || !order.getPayerUserId().equals(userId)) throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
        return order;
    }

    private SubjectType subjectType(CollectionOrder order) {
        return order.getPersonalCodeId() == null ? SubjectType.COLLECTION_REQUEST_ORDER : SubjectType.PERSONAL_QR_ORDER;
    }
    private SourceType sourceType(CollectionOrder order) {
        return order.getPersonalCodeId() == null ? SourceType.COLLECTION_REQUEST_ORDER : SourceType.PERSONAL_QR_ORDER;
    }
    private FundingSource resolveFundingSource(CollectionOrder order, Confirmation confirmation) {
        for (FundingSource source : new FundingSource[] { FundingSource.BALANCE, FundingSource.MINI_CREDIT }) {
            if (Arrays.equals(confirmation.getSubjectHash(), subjectHash(order, source))) return source;
        }
        return null;
    }

    private byte[] subjectHash(CollectionOrder order, FundingSource fundingSource) {
        return security.digest(order.getOrderId() + "\n" + order.getVersion() + "\n" + order.getPayerUserId() + "\n"
                + order.getPayerAccountId() + "\n" + order.getPayeeUserId() + "\n" + order.getPayeeAccountId() + "\n"
                + order.getAmountFen() + "\n" + fundingSource.name() + "\n" + (order.getRequestId() == null ? "" : order.getRequestId()));
    }
    private String validTraceId(String traceId) { return traceId != null && traceId.length() == 32 ? traceId : security.newTraceId(); }
    private static void afterCommit(Runnable action) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    /** 一次性确认令牌签发结果。 */
    public record IssuedConfirmation(String confirmationToken, String subjectHash, Instant expiresAt) { }
}
