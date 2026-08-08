package com.minialalipay.business.application.qrpay;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.application.port.QrPayStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.application.risk.RiskEvaluationService;
import com.minialalipay.business.application.risk.RiskReviewRouter;
import com.minialalipay.business.domain.confirmation.Confirmation;
import com.minialalipay.business.domain.confirmation.ConfirmationStatus;
import com.minialalipay.business.domain.confirmation.SubjectType;
import com.minialalipay.business.domain.qrpay.QrPayOrder;
import com.minialalipay.business.domain.qrpay.QrPayOrderEvent;
import com.minialalipay.business.domain.qrpay.QrPayOrderStatus;
import com.minialalipay.business.domain.qrpay.QrTokenDigest;
import com.minialalipay.business.domain.risk.RiskDecisionStatus;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.business.domain.transaction.TransactionStatus;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 动态二维码收款非资金流程的应用服务。
 *
 * <p>本服务只持有来源订单；资金受理复用统一确认、交易主单与 TCC 协调器，绝不直接修改余额、额度或账本。</p>
 */
@Service
public class QrPayApplicationService {
    private static final String CREATE_OPERATION = "CREATE_QR_PAY";
    private static final String CANCEL_OPERATION = "CANCEL_QR_PAY";

    private final QrPayStore store;
    private final AccountDirectoryPort accounts;
    private final SecurityMaterialPort security;
    private final IdempotencyKeyValidator keyValidator;
    private final Clock clock;
    private final BusinessStore businessStore;
    private final PaymentProofPort paymentProofs;
    private final TccCoordinatorPort coordinator;
    private final RiskEvaluationService risk;
    private final RiskReviewRouter riskRouter;

    /** 创建二维码应用服务。 */
    @Autowired
    public QrPayApplicationService(QrPayStore store, AccountDirectoryPort accounts, SecurityMaterialPort security,
                                   IdempotencyKeyValidator keyValidator, BusinessStore businessStore,
                                   PaymentProofPort paymentProofs, TccCoordinatorPort coordinator,
                                   RiskEvaluationService risk, RiskReviewRouter riskRouter) {
        this(store, accounts, security, keyValidator, businessStore, paymentProofs, coordinator, risk, riskRouter, Clock.systemUTC());
    }

    /** 仅供无资金依赖的来源订单切片测试构造；生产环境必须使用完整构造器。 */
    public QrPayApplicationService(QrPayStore store, AccountDirectoryPort accounts, SecurityMaterialPort security,
                                   IdempotencyKeyValidator keyValidator) {
        this(store, accounts, security, keyValidator, null, null, null, null, null, Clock.systemUTC());
    }

    QrPayApplicationService(QrPayStore store, AccountDirectoryPort accounts, SecurityMaterialPort security,
                            IdempotencyKeyValidator keyValidator, Clock clock) {
        this(store, accounts, security, keyValidator, null, null, null, null, null, clock);
    }

    QrPayApplicationService(QrPayStore store, AccountDirectoryPort accounts, SecurityMaterialPort security,
                            IdempotencyKeyValidator keyValidator, BusinessStore businessStore,
                            PaymentProofPort paymentProofs, TccCoordinatorPort coordinator,
                            RiskEvaluationService risk, RiskReviewRouter riskRouter, Clock clock) {
        this.store = store;
        this.accounts = accounts;
        this.security = security;
        this.keyValidator = keyValidator;
        this.businessStore = businessStore;
        this.paymentProofs = paymentProofs;
        this.coordinator = coordinator;
        this.risk = risk;
        this.riskRouter = riskRouter;
        this.clock = clock;
    }

    /**
     * 创建当前用户本人的动态收款订单。
     *
     * @return 原始短期令牌仅在本次返回中携带，调用方不得持久化或记录日志
     */
    @Transactional
    public CreatedOrder create(String userId, long amountFen, String subject, String idempotencyKey) {
        if (!keyValidator.isValid(idempotencyKey) || amountFen < 1 || amountFen > 5_000_000L) {
            throw new BusinessException(amountFen < 1 || amountFen > 5_000_000L
                    ? BusinessErrorCode.AMOUNT_OUT_OF_RANGE : CommonErrorCode.INVALID_REQUEST);
        }
        String normalizedSubject = normalizeSubject(subject);
        byte[] requestDigest = security.digest(amountFen + "|" + normalizedSubject);
        QrPayStore.IdempotencyRecord existing = store.findIdempotency(userId, CREATE_OPERATION, idempotencyKey).orElse(null);
        if (existing != null) return replay(existing, requestDigest, userId);

        var account = accounts.resolvePersonalAccount(userId);
        if (!"ACTIVE".equals(account.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        String rawToken = security.newQrToken();
        byte[] tokenDigestBytes = security.digest(rawToken);
        Instant now = clock.instant();
        QrPayOrder order = QrPayOrder.create(security.newId(), userId, account.accountId(), amountFen,
                normalizedSubject, QrTokenDigest.fromHex(java.util.HexFormat.of().formatHex(tokenDigestBytes)), now);
        if (!store.create(order, tokenDigestBytes, security.newId(), userId, idempotencyKey, requestDigest)) {
            return replay(store.findIdempotency(userId, CREATE_OPERATION, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("二维码订单幂等占位冲突后未找到既有记录")), requestDigest, userId);
        }
        return new CreatedOrder(order, rawToken);
    }

    /** 交换令牌并将订单限制到同一个 H5 引导会话。 */
    @Transactional
    public QrPayOrder exchange(String bootstrapSessionId, String rawToken) {
        QrPayOrder order = store.findByTokenDigest(security.digest(rawToken))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.QR_TOKEN_INVALID));
        ensureActive(order);
        long expectedVersion = order.getVersion();
        try {
            order.exchangeToken(security.stableId(bootstrapSessionId),
                    QrTokenDigest.fromHex(java.util.HexFormat.of().formatHex(security.digest(rawToken))), clock.instant());
        } catch (IllegalArgumentException invalid) {
            throw new BusinessException(BusinessErrorCode.QR_TOKEN_INVALID);
        } catch (IllegalStateException invalid) {
            throw new BusinessException(BusinessErrorCode.QR_TOKEN_CONSUMED);
        }
        if (order.getVersion() != expectedVersion && !store.update(order, expectedVersion)) {
            throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        }
        return order;
    }

    /** 标记绑定 H5 会话已完成扫码。 */
    @Transactional
    public QrPayOrder scan(String orderId, String bootstrapSessionId) {
        QrPayOrder order = store.findById(orderId).orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
        ensureActive(order);
        long expectedVersion = order.getVersion();
        try {
            order.scan(security.stableId(bootstrapSessionId), expectedVersion, clock.instant());
        } catch (IllegalStateException invalid) {
            throw new BusinessException(BusinessErrorCode.ORDER_STATE_INVALID);
        }
        if (order.getVersion() != expectedVersion && !store.update(order, expectedVersion)) {
            throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        }
        return order;
    }

    /** 仅允许订单收款人读取自己的订单。 */
    @Transactional
    public QrPayOrder getForPayee(String userId, String orderId) {
        QrPayOrder order = store.findById(orderId).orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
        if (!order.getPayeeUserId().equals(userId)) throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
        ensureActive(order);
        return refreshTransactionStatus(order);
    }

    /**
     * 查询二维码订单并执行付款人、收款人或绑定 H5 会话三类对象级授权。
     *
     * <p>订单进入 {@code PROCESSING} 后必须回源统一交易主单；来源订单只是投影，不能自行推断资金终态。</p>
     */
    @Transactional(readOnly = true)
    public QrPayOrder getForAuthorizedUser(String userId, String bootstrapSessionId, String orderId) {
        QrPayOrder order = requiredOrder(orderId);
        boolean sessionBound = bootstrapSessionId != null
                && security.stableId(bootstrapSessionId).equals(order.getBoundBootstrapSessionId());
        if (!order.getPayeeUserId().equals(userId) && !userId.equals(order.getPayerUserId()) && !sessionBound) {
            throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
        }
        ensureActive(order);
        return refreshTransactionStatus(order);
    }

    /** 查询当前用户创建的二维码订单。 */
    @Transactional(readOnly = true)
    public List<QrPayOrder> listForPayee(String userId, String status, int limit) {
        return store.findByPayeeUserId(userId, status, Math.min(Math.max(limit, 1), 100)).stream()
                .map(this::refreshTransactionStatus).toList();
    }

    /** 校验短期二维码令牌可用于创建 H5 引导会话，但不返回订单数据也不绑定会话。 */
    @Transactional
    public void validateBootstrapToken(String rawToken) {
        QrPayOrder order = store.findByTokenDigest(security.digest(rawToken))
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.QR_TOKEN_INVALID));
        ensureActive(order);
    }

    /** 取消未受理的二维码订单；同一幂等键同参返回既有订单。 */
    @Transactional
    public QrPayOrder cancel(String userId, String orderId, long version, String idempotencyKey) {
        if (!keyValidator.isValid(idempotencyKey)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        byte[] requestDigest = security.digest(orderId + "|" + version);
        QrPayStore.IdempotencyRecord existing = store.findIdempotency(userId, CANCEL_OPERATION, idempotencyKey).orElse(null);
        if (existing != null) return replayCancellation(existing, requestDigest, userId);
        QrPayOrder order = getForPayee(userId, orderId);
        ensureActive(order);
        if (!store.reserveIdempotency(security.newId(), userId, CANCEL_OPERATION, idempotencyKey, requestDigest, orderId)) {
            return replayCancellation(store.findIdempotency(userId, CANCEL_OPERATION, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("二维码取消幂等占位冲突后未找到既有记录")), requestDigest, userId);
        }
        try {
            order.cancel(version, clock.instant());
        } catch (IllegalStateException invalid) {
            throw new BusinessException(order.getVersion() != version ? BusinessErrorCode.VERSION_CONFLICT : BusinessErrorCode.ORDER_NOT_CANCELLABLE);
        }
        if (!store.update(order, version)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        return order;
    }

    /**
     * 校验支付证明并为绑定 H5 会话的付款人签发二维码确认令牌。
     *
     * <p>订单 CAS、支付密码版本和资金来源全部进入摘要，因此任何一项变化都会使旧确认不可消费。</p>
     */
    @Transactional
    public IssuedConfirmation issueConfirmation(String userId, String orderId, String bootstrapSessionId,
                                                long version, String paymentProof, FundingSource fundingSource) {
        if (fundingSource == null) throw new BusinessException(BusinessErrorCode.FUNDING_SOURCE_NOT_ALLOWED);
        requirePaymentDependencies();
        QrPayOrder order = requiredOrder(orderId);
        requireBoundSession(order, bootstrapSessionId);
        var payer = accounts.resolvePersonalAccount(userId);
        if (!"ACTIVE".equals(payer.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        if (fundingSource == FundingSource.MINI_CREDIT) {
            accounts.requireCreditPaymentEligible(userId, order.getAmountFen());
        }
        PaymentProofPort.VerifiedProof proof = paymentProofs.verify(userId, paymentProof, "QR_PAY_CONFIRM");
        long expectedVersion = order.getVersion();
        if (expectedVersion != version) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        try {
            order.lockPayer(userId, payer.accountId(), version, clock.instant());
        } catch (IllegalStateException invalid) {
            throw new BusinessException(invalid.getMessage().contains("本人")
                    ? BusinessErrorCode.SELF_PAYMENT_FORBIDDEN : BusinessErrorCode.ORDER_STATE_INVALID);
        }
        routeOnRiskVerdict(SubjectType.QR_PAY_ORDER.name(), order, expectedVersion);
        String rawToken = security.newConfirmationToken();
        Confirmation confirmation = Confirmation.issue(security.newId(), security.digest(rawToken), SubjectType.QR_PAY_ORDER,
                orderId, subjectHash(order, fundingSource, proof.payPasswordVersion()), userId, proof.paymentProofId(), proof.payPasswordVersion(), clock.instant());
        businessStore.replaceQrPayConfirmation(confirmation, expectedVersion, order);
        return new IssuedConfirmation(rawToken, "sha256:" + java.util.HexFormat.of().formatHex(subjectHash(order, fundingSource, proof.payPasswordVersion())),
                confirmation.getExpiresAt());
    }

    /**
     * 签发确认令牌前的风控预检。
     *
     * <p>风控拒绝时直接拦截且不创建资金交易；命中转人工规则时由独立事务把订单置为 {@code RISK_REVIEW}
     * 并创建预检工单，再以 202 告知客户端已进入人工审核。未配置风控评估（切片测试）时默认放行。
     * 评估依据订单中的付款人、收款账户与金额，调用风控规则引擎并落库决策。</p>
     */
    private void routeOnRiskVerdict(String subjectType, QrPayOrder order, long expectedVersion) {
        if (risk == null) return;
        RiskEvaluationService.RiskVerdict verdict = risk.evaluatePrecheck(subjectType, order.getOrderId(),
                order.getPayerUserId(), order.getPayeeAccountId(), order.getAmountFen(), clock.instant());
        if (verdict.status() == RiskDecisionStatus.REJECT) {
            throw new BusinessException(BusinessErrorCode.RISK_REJECTED);
        }
        if (verdict.status() == RiskDecisionStatus.MANUAL_REVIEW) {
            riskRouter.routeQrPayOrderToReview(order, expectedVersion, verdict.reasonCode(), clock.instant());
            throw new BusinessException(BusinessErrorCode.RISK_MANUAL_REVIEW);
        }
    }

    /**
     * 原子受理二维码余额支付；同一来源订单只能形成一笔统一资金交易。
     */
    @Transactional
    public FundTransaction pay(String userId, String orderId, String bootstrapSessionId, String confirmationToken,
                               String idempotencyKey, String traceId) {
        requirePaymentDependencies();
        if (!keyValidator.isValid(idempotencyKey)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        QrPayOrder order = requiredOrder(orderId);
        requireBoundSession(order, bootstrapSessionId);
        byte[] tokenDigest = security.digest(confirmationToken);
        byte[] requestHash = security.digest(orderId + "\n" + Base64.getEncoder().encodeToString(tokenDigest));
        Confirmation confirmation = businessStore.findConfirmationForUpdate(tokenDigest)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.CONFIRMATION_MISMATCH));
        FundingSource fundingSource = resolveFundingSource(order, confirmation);
        TransactionType transactionType = fundingSource == FundingSource.MINI_CREDIT
                ? TransactionType.CREDIT_PAY : TransactionType.QR_PAY;
        var repeated = businessStore.findByIdempotency(userId, transactionType, idempotencyKey).orElse(null);
        if (repeated != null) {
            if (!Arrays.equals(repeated.requestHash(), requestHash)) throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
            return repeated.transaction();
        }
        var sameSource = businessStore.findBySource(SourceType.QR_PAY_ORDER.name(), orderId).orElse(null);
        if (sameSource != null) return sameSource.transaction();
        if (confirmation.getSubjectType() != SubjectType.QR_PAY_ORDER || !confirmation.getSubjectId().equals(orderId)
                || !confirmation.getPayerUserId().equals(userId)
                || !Arrays.equals(confirmation.getSubjectHash(), subjectHash(order, fundingSource,
                confirmation.getPayPasswordVersion()))) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_STALE);
        }
        if (paymentProofs.currentPayPasswordVersion(userId) != confirmation.getPayPasswordVersion()) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_STALE);
        }
        Instant now = clock.instant();
        try { confirmation.consume(now); }
        catch (IllegalStateException invalid) {
            throw new BusinessException(confirmation.getStatus() == ConfirmationStatus.EXPIRED
                    ? BusinessErrorCode.CONFIRMATION_EXPIRED : BusinessErrorCode.CONFIRMATION_MISMATCH);
        }
        if (!businessStore.updateConfirmation(confirmation, ConfirmationStatus.ACTIVE.name())) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_MISMATCH);
        }
        String transactionId = security.newId();
        long expectedVersion = order.getVersion();
        try { order.acceptByFundTransaction(expectedVersion, transactionId, now); }
        catch (IllegalStateException invalid) { throw new BusinessException(BusinessErrorCode.ORDER_STATE_INVALID); }
        if (!store.update(order, expectedVersion)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        FundTransaction transaction = FundTransaction.accept(transactionId, transactionType, SourceType.QR_PAY_ORDER,
                orderId, userId, order.getPayerAccountId(), order.getPayeeAccountId(), fundingSource, order.getAmountFen(),
                idempotencyKey, "LOW", validTraceId(traceId), now);
        businessStore.createTransaction(transaction, requestHash, security.newId(), now);
        store.appendOrderEvent(new QrPayOrderEvent(security.newId(), orderId, transactionId, QrPayOrderStatus.PROCESSING.name(), now));
        afterCommit(() -> coordinator.startOrResume(transaction));
        return transaction;
    }

    /** 从确认摘要反推出已授权的资金来源，防止支付请求覆盖用户在确认阶段的选择。 */
    private FundingSource resolveFundingSource(QrPayOrder order, Confirmation confirmation) {
        long passwordVersion = confirmation.getPayPasswordVersion();
        if (Arrays.equals(confirmation.getSubjectHash(), subjectHash(order, FundingSource.BALANCE, passwordVersion))) {
            return FundingSource.BALANCE;
        }
        if (Arrays.equals(confirmation.getSubjectHash(), subjectHash(order, FundingSource.MINI_CREDIT, passwordVersion))) {
            return FundingSource.MINI_CREDIT;
        }
        throw new BusinessException(BusinessErrorCode.CONFIRMATION_STALE);
    }

    private CreatedOrder replay(QrPayStore.IdempotencyRecord existing, byte[] requestDigest, String userId) {
        if (!Arrays.equals(existing.requestDigest(), requestDigest)) throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        return new CreatedOrder(getForPayee(userId, existing.orderId()), null);
    }

    private QrPayOrder replayCancellation(QrPayStore.IdempotencyRecord existing, byte[] requestDigest, String userId) {
        if (!Arrays.equals(existing.requestDigest(), requestDigest)) throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        return getForPayee(userId, existing.orderId());
    }

    private void ensureActive(QrPayOrder order) {
        if (order.getStatus() == QrPayOrderStatus.PROCESSING || order.getStatus() == QrPayOrderStatus.COMPENSATING
                || order.getStatus() == QrPayOrderStatus.MANUAL_REVIEW || order.getStatus() == QrPayOrderStatus.SUCCESS
                || order.getStatus() == QrPayOrderStatus.CANCELLED || order.getStatus() == QrPayOrderStatus.REJECTED) {
            return;
        }
        Instant now = clock.instant();
        long expectedVersion = order.getVersion();
        if (order.expireIfNecessary(now)) {
            if (!store.update(order, expectedVersion)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
            throw new BusinessException(BusinessErrorCode.ORDER_EXPIRED);
        }
        if (!now.isBefore(order.getExpiresAt()) || order.getStatus().name().equals("EXPIRED")) {
            throw new BusinessException(BusinessErrorCode.ORDER_EXPIRED);
        }
    }

    private QrPayOrder requiredOrder(String orderId) {
        return store.findById(orderId).orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
    }

    /** 使用统一交易的权威状态构造只读视图，不在读取路径修改订单或资金事实。 */
    private QrPayOrder refreshTransactionStatus(QrPayOrder order) {
        if (businessStore == null || order.getTransactionId() == null) return order;
        return businessStore.findTransaction(order.getTransactionId()).map(record -> {
            QrPayOrderStatus status = switch (record.transaction().getStatus()) {
                case PROCESSING -> QrPayOrderStatus.PROCESSING;
                case COMPENSATING -> QrPayOrderStatus.COMPENSATING;
                case MANUAL_REVIEW -> QrPayOrderStatus.MANUAL_REVIEW;
                case SUCCESS -> QrPayOrderStatus.SUCCESS;
                case CANCELLED, REVERSED -> QrPayOrderStatus.CANCELLED;
            };
            if (status == order.getStatus()) return order;
            return new QrPayOrder(order.getOrderId(), order.getPayeeUserId(), order.getPayeeAccountId(), order.getAmountFen(),
                    order.getSubject(), order.getTokenDigest(), status, order.getBoundBootstrapSessionId(), order.getPayerUserId(),
                    order.getPayerAccountId(), order.getTransactionId(), order.getVersion(), order.getExpiresAt(),
                    order.getCreatedAt(), record.transaction().getUpdatedAt());
        }).orElse(order);
    }

    private void requireBoundSession(QrPayOrder order, String bootstrapSessionId) {
        if (bootstrapSessionId == null || !security.stableId(bootstrapSessionId).equals(order.getBoundBootstrapSessionId())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    private byte[] subjectHash(QrPayOrder order, FundingSource fundingSource, long payPasswordVersion) {
        return security.digest(order.getOrderId() + "\n" + order.getVersion() + "\n" + order.getPayeeUserId() + "\n"
                + order.getPayeeAccountId() + "\n" + order.getPayerUserId() + "\n" + order.getPayerAccountId() + "\n"
                + order.getAmountFen() + "\n" + fundingSource.name() + "\n" + payPasswordVersion);
    }

    private void requirePaymentDependencies() {
        if (businessStore == null || paymentProofs == null || coordinator == null) {
            throw new IllegalStateException("二维码支付统一交易依赖未配置");
        }
    }

    private String validTraceId(String traceId) {
        return traceId != null && traceId.length() == 32 ? traceId : security.newTraceId();
    }

    private static void afterCommit(Runnable action) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    private static String normalizeSubject(String subject) {
        if (subject == null || subject.isBlank()) return "动态收款";
        String normalized = subject.replaceAll("[\\p{Cntrl}]", "").trim();
        if (normalized.isBlank() || normalized.length() > 50) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        return normalized;
    }

    /** 创建响应中的订单和一次性原始令牌。 */
    public record CreatedOrder(QrPayOrder order, String rawToken) { }
    /** 确认签发结果；原始令牌只存在于本次响应。 */
    public record IssuedConfirmation(String confirmationToken, String subjectHash, Instant expiresAt) { }
}
