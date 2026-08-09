package com.minialalipay.business.application.refund;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.RefundStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.domain.refund.RefundOrder;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionStatus;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * 受控退款来源订单应用服务。
 *
 * <p>只对本人作为收款方的已成功动态扫码交易（QR_PAY/CREDIT_PAY）创建退款订单并提交受理；
 * 资金冲正复用统一 REFUND 交易与 TCC，绝不直接修改余额或账本。账户中心尚未提供退款 TCC
 * 参与者前，受理会安全失败并转入人工处置，绝不伪造冲正。</p>
 */
@Service
public class RefundApplicationService {
    private static final String CREATE_OPERATION = "CREATE_REFUND";
    private static final String SUBMIT_OPERATION = "SUBMIT_REFUND";

    private final RefundStore store;
    private final BusinessStore businessStore;
    private final AccountDirectoryPort accounts;
    private final SecurityMaterialPort security;
    private final IdempotencyKeyValidator keyValidator;
    private final TccCoordinatorPort coordinator;
    private final Clock clock;

    /** 创建退款应用服务。 */
    @Autowired
    public RefundApplicationService(RefundStore store, BusinessStore businessStore, AccountDirectoryPort accounts,
                                    SecurityMaterialPort security, IdempotencyKeyValidator keyValidator,
                                    TccCoordinatorPort coordinator) {
        this(store, businessStore, accounts, security, keyValidator, coordinator, Clock.systemUTC());
    }

    /** 供测试注入固定时钟构造。 */
    public RefundApplicationService(RefundStore store, BusinessStore businessStore, AccountDirectoryPort accounts,
                                    SecurityMaterialPort security, IdempotencyKeyValidator keyValidator,
                                    TccCoordinatorPort coordinator, Clock clock) {
        this.store = store;
        this.businessStore = businessStore;
        this.accounts = accounts;
        this.security = security;
        this.keyValidator = keyValidator;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    /**
     * 对本人已成功的动态扫码交易创建受控退款订单。
     *
     * @param userId 当前登录用户，必须为原收款方
     * @param originalTransactionId 原 QR_PAY/CREDIT_PAY 统一交易号
     * @param reasonCode 退款原因
     * @param idempotencyKey 创建请求幂等键
     * @return 同键同参返回既有订单；新请求返回待提交退款订单
     */
    @Transactional
    public RefundOrder create(String userId, String originalTransactionId, String reasonCode, String idempotencyKey) {
        if (!keyValidator.isValid(idempotencyKey)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        byte[] requestHash = security.digest(originalTransactionId + "\n" + reasonCode);
        RefundStore.IdempotencyRecord existing = store.findIdempotency(userId, CREATE_OPERATION, idempotencyKey).orElse(null);
        if (existing != null) return replay(existing, requestHash, userId);

        var account = accounts.resolvePersonalAccount(userId);
        if (!"ACTIVE".equals(account.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);

        var original = businessStore.findTransaction(originalTransactionId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.TRANSACTION_NOT_FOUND));
        FundTransaction originalTransaction = original.transaction();
        requireRefundable(originalTransaction, account.accountId());
        if (store.findByOriginalTransactionId(originalTransactionId).isPresent()) {
            throw new BusinessException(BusinessErrorCode.REFUND_ALREADY_EXISTS);
        }

        Instant now = clock.instant();
        String orderId = security.newId();
        RefundOrder order = RefundOrder.create(orderId, originalTransactionId, userId, account.accountId(),
                originalTransaction.getInitiatorUserId(), originalTransaction.getPayerAccountId(),
                originalTransaction.getBusinessType().name(), originalTransaction.getFundingSource().name(),
                originalTransaction.getAmountFen(), reasonCode, now);
        if (!store.reserveIdempotency(security.newId(), userId, CREATE_OPERATION, idempotencyKey, requestHash, orderId)) {
            return replay(store.findIdempotency(userId, CREATE_OPERATION, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("退款创建幂等占位冲突后未读取到既有记录")), requestHash, userId);
        }
        if (!store.create(order)) {
            throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        }
        return order;
    }

    /**
     * 提交执行退款，受理唯一 REFUND 统一交易并启动 TCC。
     *
     * <p>订单 CAS 从创建推进到处理中并绑定交易主单；余额与账本冲正由账户中心 TCC 分支核验，
     * 账户中心尚未提供退款 TCC 参与者前会安全失败转人工处置。</p>
     */
    @Transactional
    public RefundOrder submit(String userId, String refundOrderId, long version, String idempotencyKey, String traceId) {
        if (!keyValidator.isValid(idempotencyKey)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        byte[] requestHash = security.digest(refundOrderId + "\n" + version);
        RefundStore.IdempotencyRecord existing = store.findIdempotency(userId, SUBMIT_OPERATION, idempotencyKey).orElse(null);
        if (existing != null) return get(userId, existing.resourceId());

        RefundOrder order = get(userId, refundOrderId);
        if (!store.reserveIdempotency(security.newId(), userId, SUBMIT_OPERATION, idempotencyKey, requestHash, refundOrderId)) {
            return get(userId, refundOrderId);
        }
        Instant now = clock.instant();
        long expectedVersion = order.getVersion();
        String transactionId = security.newId();
        try {
            order.submit(expectedVersion, transactionId, now);
        } catch (IllegalStateException invalid) {
            throw new BusinessException(BusinessErrorCode.ORDER_STATE_INVALID);
        }
        if (!store.update(order, expectedVersion)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        FundTransaction transaction = FundTransaction.acceptRefund(transactionId, SourceType.REFUND_ORDER,
                refundOrderId, userId, order.getMerchantAccountId(), order.getPayerAccountId(),
                FundingSource.valueOf(order.getFundingSource()), order.getAmountFen(), idempotencyKey, "LOW",
                validTraceId(traceId), order.getOriginalTransactionId(), now);
        businessStore.createTransaction(transaction, security.digest(refundOrderId), security.newId(), now);
        afterCommit(() -> coordinator.startOrResume(transaction));
        return order;
    }

    /** 查询退款发起人本人订单。 */
    @Transactional(readOnly = true)
    public RefundOrder get(String userId, String refundOrderId) {
        RefundOrder order = store.findById(refundOrderId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
        if (!order.getMerchantUserId().equals(userId)) throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
        return order;
    }

    /** 查询退款发起人本人的退款订单列表。 */
    @Transactional(readOnly = true)
    public List<RefundOrder> list(String userId, String status, int limit) {
        return store.findByMerchantUserId(userId, status, Math.min(Math.max(limit, 1), 100));
    }

    /** 校验原交易为本人收款的已成功动态扫码交易（QR_PAY 或 CREDIT_PAY）。 */
    private void requireRefundable(FundTransaction original, String merchantAccountId) {
        if (original.getBusinessType() != TransactionType.QR_PAY
                && original.getBusinessType() != TransactionType.CREDIT_PAY) {
            throw new BusinessException(BusinessErrorCode.REFUND_NOT_ALLOWED);
        }
        if (original.getStatus() != TransactionStatus.SUCCESS) throw new BusinessException(BusinessErrorCode.REFUND_NOT_ALLOWED);
        if (!original.getPayeeAccountId().equals(merchantAccountId)) throw new BusinessException(BusinessErrorCode.REFUND_NOT_ALLOWED);
    }

    private RefundOrder replay(RefundStore.IdempotencyRecord existing, byte[] requestHash, String userId) {
        if (!Arrays.equals(existing.requestDigest(), requestHash)) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return get(userId, existing.resourceId());
    }

    private String validTraceId(String traceId) {
        return traceId != null && traceId.length() == 32 ? traceId : security.newTraceId();
    }

    private static void afterCommit(Runnable action) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { action.run(); }
                });
    }
}
