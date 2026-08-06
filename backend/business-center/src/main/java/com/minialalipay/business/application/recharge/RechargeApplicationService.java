package com.minialalipay.business.application.recharge;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.RechargeStore;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.domain.recharge.RechargeDailyUsage;
import com.minialalipay.business.domain.recharge.RechargeOrder;
import com.minialalipay.business.domain.recharge.RechargeOrderStatus;
import com.minialalipay.business.domain.recharge.RechargePolicy;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

/**
 * 受控模拟充值订单应用服务。
 *
 * <p>在同一 business_db 事务中完成登录用户账户解析、限额快照、幂等、日额度预占和订单创建；
 * 当前仅返回待渠道状态，不创建 {@code fund_transaction}，不调用 TCC 或账户写接口。</p>
 */
@Service
public class RechargeApplicationService {
    private static final String OPERATION = "CREATE_RECHARGE";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final RechargeStore store;
    private final AccountDirectoryPort accounts;
    private final SecurityMaterialPort secure;
    private final IdempotencyKeyValidator keyValidator;
    private final BusinessStore businessStore;
    private final TccCoordinatorPort coordinator;
    private final Clock clock;

    /** 充值付款对手：账户中心虚拟资金发行权益科目，正常方向为贷方（仅演示虚拟资金）。 */
    private static final String VIRTUAL_ISSUANCE_ACCOUNT = "SYS00000000000000000000001";

    /** 创建充值应用服务。 */
    @Autowired
    public RechargeApplicationService(RechargeStore store, AccountDirectoryPort accounts, SecurityMaterialPort secure,
                                      IdempotencyKeyValidator keyValidator, BusinessStore businessStore,
                                      TccCoordinatorPort coordinator) {
        this(store, accounts, secure, keyValidator, businessStore, coordinator, Clock.systemUTC());
    }

    RechargeApplicationService(RechargeStore store, AccountDirectoryPort accounts, SecurityMaterialPort secure,
                               IdempotencyKeyValidator keyValidator, BusinessStore businessStore,
                               TccCoordinatorPort coordinator, Clock clock) {
        this.store = store;
        this.accounts = accounts;
        this.secure = secure;
        this.keyValidator = keyValidator;
        this.businessStore = businessStore;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    /**
     * 创建待渠道处理的模拟充值订单。
     *
     * @param userId 当前登录用户
     * @param amountFen 充值金额，单位分
     * @param idempotencyKey 创建请求幂等键
     * @return 同键同参返回既有订单；新请求返回待渠道订单
     */
    @Transactional
    public RechargeOrder create(String userId, long amountFen, String idempotencyKey) {
        if (!keyValidator.isValid(idempotencyKey)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        byte[] requestHash = secure.digest(String.valueOf(amountFen));
        RechargeStore.IdempotencyRecord existing = store.findIdempotency(userId, idempotencyKey).orElse(null);
        if (existing != null) return existingOrder(existing, requestHash, userId);

        RechargePolicy policy = store.getActivePolicy();
        try {
            policy.validateAmount(amountFen);
        } catch (IllegalStateException invalid) {
            throw new BusinessException(BusinessErrorCode.RECHARGE_LIMIT_EXCEEDED);
        }
        var account = accounts.resolvePersonalAccount(userId);
        if (!"ACTIVE".equals(account.status())) throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);

        Instant now = clock.instant();
        LocalDate businessDate = now.atZone(BUSINESS_ZONE).toLocalDate();
        RechargeDailyUsage usage = store.findDailyUsage(userId, businessDate)
                .orElseGet(() -> RechargeDailyUsage.empty(userId, businessDate, now));
        long expectedUsageVersion = usage.getVersion();
        try {
            usage.reserve(expectedUsageVersion, amountFen, policy, now);
        } catch (IllegalStateException invalid) {
            throw new BusinessException(BusinessErrorCode.RECHARGE_LIMIT_EXCEEDED);
        }
        String orderId = secure.newId();
        if (!store.reserveIdempotency(secure.newId(), userId, idempotencyKey, requestHash, orderId)) {
            return existingOrder(store.findIdempotency(userId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("充值幂等占位冲突后未读取到既有记录")), requestHash, userId);
        }
        RechargeOrder order = RechargeOrder.create(orderId, userId, account.accountId(), amountFen,
                businessDate, policy, now);
        if (!store.createOrderAndUpdateUsage(order, usage, expectedUsageVersion)) {
            throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        }
        return order;
    }

    /** 查询当前用户自己的充值订单。 */
    @Transactional(readOnly = true)
    public RechargeOrder get(String userId, String rechargeOrderId) {
        RechargeOrder order = store.findOrder(rechargeOrderId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
        if (!order.getUserId().equals(userId)) throw new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND);
        return order;
    }

    /**
     * 记录受控模拟渠道结果。
     *
     * <p>渠道成功时在同一 business_db 事务中推进充值订单到 {@code PROCESSING} 并创建 {@code RECHARGE} 统一交易，
     * 提交后启动 TCC；账户中心尚未提供充值 TCC 参与者与虚拟发行权益科目种子前，TCC 会安全失败并转入人工处置，
     * 绝不伪造入账。重复回调对已受理或已拒绝订单幂等。</p>
     *
     * @param rechargeOrderId 充值订单号
     * @param channelSuccess 模拟渠道是否成功
     * @param rejectReasonCode 渠道拒绝原因码，成功时可为空
     * @param traceId 链路编号
     * @return 更新后的充值订单
     */
    @Transactional
    public RechargeOrder onChannelResult(String rechargeOrderId, boolean channelSuccess,
                                         String rejectReasonCode, String traceId) {
        RechargeOrder order = store.findOrder(rechargeOrderId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.ORDER_NOT_FOUND));
        long expectedVersion = order.getVersion();
        Instant now = clock.instant();
        if (order.getStatus() == RechargeOrderStatus.PROCESSING
                || order.getStatus() == RechargeOrderStatus.REJECTED) {
            return order;
        }
        if (!channelSuccess) {
            try {
                order.rejectByChannel(expectedVersion, rejectReasonCode, now);
            } catch (IllegalStateException invalid) {
                throw new BusinessException(invalid.getMessage() != null && invalid.getMessage().contains("版本")
                        ? BusinessErrorCode.VERSION_CONFLICT : BusinessErrorCode.ORDER_STATE_INVALID);
            }
            if (!store.updateOrder(order, expectedVersion)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
            return order;
        }
        String transactionId = secure.newId();
        try {
            order.acceptFundTransaction(expectedVersion, transactionId, now);
        } catch (IllegalStateException invalid) {
            throw new BusinessException(invalid.getMessage() != null && invalid.getMessage().contains("版本")
                    ? BusinessErrorCode.VERSION_CONFLICT : BusinessErrorCode.ORDER_STATE_INVALID);
        }
        if (!store.updateOrder(order, expectedVersion)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        FundTransaction transaction = FundTransaction.accept(transactionId, TransactionType.RECHARGE, SourceType.RECHARGE_ORDER,
                rechargeOrderId, order.getUserId(), VIRTUAL_ISSUANCE_ACCOUNT, order.getTargetAccountId(),
                FundingSource.BALANCE, order.getAmountFen(), secure.stableId("RECHARGE:" + rechargeOrderId), "LOW",
                validTraceId(traceId), now);
        businessStore.createTransaction(transaction, secure.digest(rechargeOrderId), secure.newId(), now);
        afterCommit(() -> coordinator.startOrResume(transaction));
        return order;
    }

    private String validTraceId(String traceId) {
        return traceId != null && traceId.length() == 32 ? traceId : secure.newTraceId();
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

    private RechargeOrder existingOrder(RechargeStore.IdempotencyRecord existing, byte[] requestHash, String userId) {
        if (!Arrays.equals(existing.requestHash(), requestHash)) {
            throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT);
        }
        return get(userId, existing.rechargeOrderId());
    }
}
