package com.minialalipay.business.application.bankcard;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BankCardPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executor;

/**
 * 银行卡充值应用服务：从银行卡虚拟余额向用户账户余额入账（银行卡给账户充钱）。
 *
 * <p>编排在同一本地事务内串行执行：用户中心验密并签发证明 → 校验银行卡归属和余额充足 →
 * 创建 FundTransaction（BANK_CARD_RECHARGE）→ 提交后异步驱动 Seata 全局事务。</p>
 *
 * <p>银行卡余额扣减由 account-center 的 Seata TCC 分支完成，business-center 只负责
 * 交易主单编排和终态发布。</p>
 */
@Service
public class BankCardRechargeApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BankCardRechargeApplicationService.class);

    private final BankCardPort bankCards;
    private final AccountDirectoryPort accounts;
    private final PaymentProofPort paymentProofs;
    private final SecurityMaterialPort secure;
    private final BusinessStore store;
    private final TccCoordinatorPort coordinator;
    private final IdempotencyKeyValidator keyValidator;
    private final Executor coordinationExecutor;
    private final Clock clock;

    @Autowired
    public BankCardRechargeApplicationService(BankCardPort bankCards, AccountDirectoryPort accounts,
                                               PaymentProofPort paymentProofs, SecurityMaterialPort secure,
                                               BusinessStore store, TccCoordinatorPort coordinator,
                                               IdempotencyKeyValidator keyValidator,
                                               @org.springframework.beans.factory.annotation.Qualifier("transferCoordinationExecutor")
                                               Executor coordinationExecutor) {
        this(bankCards, accounts, paymentProofs, secure, store, coordinator, keyValidator,
                coordinationExecutor, Clock.systemUTC());
    }

    BankCardRechargeApplicationService(BankCardPort bankCards, AccountDirectoryPort accounts,
                                       PaymentProofPort paymentProofs, SecurityMaterialPort secure,
                                       BusinessStore store, TccCoordinatorPort coordinator,
                                       IdempotencyKeyValidator keyValidator,
                                       Executor coordinationExecutor, Clock clock) {
        this.bankCards = bankCards;
        this.accounts = accounts;
        this.paymentProofs = paymentProofs;
        this.secure = secure;
        this.store = store;
        this.coordinator = coordinator;
        this.keyValidator = keyValidator;
        this.coordinationExecutor = coordinationExecutor;
        this.clock = clock;
    }

    /**
     * 发起银行卡充值：验密 → 校验银行卡 → 创建交易 → 异步驱动 Seata 全局事务。
     *
     * @param userId 当前登录用户
     * @param cardId 银行卡 ID
     * @param amountFen 充值金额（分），必须在 1~500000000 范围内
     * @param paymentPassword 原始支付密码，仅透传用户中心验密，不落日志或持久化
     * @param idempotencyKey 请求幂等键
     * @return 已创建的 FundTransaction
     */
    @Transactional
    public FundTransaction recharge(String userId, String cardId, long amountFen,
                                    String paymentPassword, String idempotencyKey) {
        if (!keyValidator.isValid(idempotencyKey)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        validateAmount(amountFen);

        // 验密：密码错误或锁定在触碰本地事实前即拒绝
        paymentProofs.verifyAndIssueProof(userId, paymentPassword, "BANK_CARD_RECHARGE");

        // 校验银行卡归属与状态
        bankCards.requireCard(userId, cardId);

        // 解析用户个人账户作为充值目标
        var account = accounts.resolvePersonalAccount(userId);
        if (!"ACTIVE".equals(account.status())) {
            throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        }

        Instant now = clock.instant();
        String transactionId = secure.newId();
        FundTransaction transaction = FundTransaction.acceptBankCardOperation(
                transactionId, TransactionType.BANK_CARD_RECHARGE, SourceType.BANK_CARD_RECHARGE_ORDER,
                transactionId, userId, account.accountId(), cardId, FundingSource.BANK_CARD,
                amountFen, idempotencyKey, "LOW", secure.newTraceId(), now);

        byte[] requestHash = secure.digest(cardId + "\n" + amountFen);
        store.createTransaction(transaction, requestHash, secure.newId(), now);

        // TCC 协调异步执行，Seata 全局事务往返不阻塞提交响应
        afterCommit(() -> coordinationExecutor.execute(() -> {
            try {
                coordinator.startOrResume(transaction);
            } catch (RuntimeException failure) {
                LOGGER.warn("银行卡充值 TCC 异步协调失败，等待恢复扫描器接管：transactionId={}, cause={}",
                        transaction.getTransactionId(), failure.getMessage());
            }
        }));

        return transaction;
    }

    private void validateAmount(long amountFen) {
        if (amountFen < 1 || amountFen > 500_000_000L) {
            throw new BusinessException(BusinessErrorCode.AMOUNT_OUT_OF_RANGE);
        }
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
