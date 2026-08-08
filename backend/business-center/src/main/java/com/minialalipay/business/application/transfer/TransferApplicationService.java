package com.minialalipay.business.application.transfer;

import com.minialalipay.business.application.port.AccountDirectoryPort;
import com.minialalipay.business.application.port.BusinessStore;
import com.minialalipay.business.application.port.ContactArchivePort;
import com.minialalipay.business.application.port.PaymentProofPort;
import com.minialalipay.business.application.port.TccCoordinatorPort;
import com.minialalipay.business.application.port.UserInfoPort;
import com.minialalipay.business.application.port.SecurityMaterialPort;
import com.minialalipay.business.domain.confirmation.Confirmation;
import com.minialalipay.business.domain.confirmation.ConfirmationStatus;
import com.minialalipay.business.domain.confirmation.SubjectType;
import com.minialalipay.business.domain.transaction.BusinessErrorCode;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.FundingSource;
import com.minialalipay.business.domain.transaction.SourceType;
import com.minialalipay.business.domain.transaction.TransactionStatus;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.business.domain.transfer.DraftStatus;
import com.minialalipay.business.domain.transfer.TransferDraft;
import com.minialalipay.common.error.BusinessException;
import com.minialalipay.common.error.CommonErrorCode;
import com.minialalipay.common.idempotency.IdempotencyKeyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.Executor;

/**
 * 主动转账草稿、确认和统一交易应用服务。
 *
 * <p>账户归属只从账户中心解析。提交事务在 business_db 内原子消费确认、推进草稿、
 * 创建主单和 Outbox；TCC 仅在本地事务提交后启动。</p>
 */
@Service
public class TransferApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransferApplicationService.class);

    private final BusinessStore store;
    private final AccountDirectoryPort accounts;
    private final PaymentProofPort paymentProofs;
    private final TccCoordinatorPort coordinator;
    private final SecurityMaterialPort secure;
    private final IdempotencyKeyValidator keyValidator;
    private final ContactArchivePort contactArchive;
    private final UserInfoPort userInfo;
    private final Executor coordinationExecutor;
    private final Clock clock;

    @Autowired
    public TransferApplicationService(BusinessStore store, AccountDirectoryPort accounts,
                                      PaymentProofPort paymentProofs, TccCoordinatorPort coordinator,
                                      SecurityMaterialPort secure, IdempotencyKeyValidator keyValidator,
                                      ContactArchivePort contactArchive, UserInfoPort userInfo,
                                      @org.springframework.beans.factory.annotation.Qualifier("transferCoordinationExecutor")
                                      Executor coordinationExecutor) {
        this(store, accounts, paymentProofs, coordinator, secure, keyValidator, contactArchive, userInfo,
                coordinationExecutor, Clock.systemUTC());
    }
    TransferApplicationService(BusinessStore store, AccountDirectoryPort accounts,
                               PaymentProofPort paymentProofs, TccCoordinatorPort coordinator,
                               SecurityMaterialPort secure, IdempotencyKeyValidator keyValidator,
                               ContactArchivePort contactArchive, UserInfoPort userInfo,
                               Executor coordinationExecutor, Clock clock) {
        this.store = store; this.accounts = accounts; this.paymentProofs = paymentProofs;
        this.coordinator = coordinator; this.secure = secure; this.keyValidator = keyValidator;
        this.contactArchive = contactArchive; this.coordinationExecutor = coordinationExecutor;
        this.clock = clock;
        this.userInfo = userInfo;
    }

    /** 创建服务端派生账户归属的转账草稿；同键同参返回原草稿。 */
    @Transactional
    public TransferDraft createDraft(String payerUserId, String payeeUserId, long amountFen,
                                     String remark, String idempotencyKey) {
        requireKey(idempotencyKey);
        byte[] hash = secure.digest(payeeUserId + "\n" + amountFen + "\n" + text(remark));
        var existing = store.findIdempotency(payerUserId, "CREATE_TRANSFER_DRAFT", idempotencyKey).orElse(null);
        if (existing != null) {
            if (!Arrays.equals(existing.requestHash(), hash)) conflict();
            return requiredDraft(existing.resourceId(), payerUserId);
        }
        if (payerUserId.equals(payeeUserId)) throw new BusinessException(BusinessErrorCode.SELF_PAYMENT_FORBIDDEN);
        if (amountFen < 1 || amountFen > TransferDraft.MAX_AMOUNT_FEN) {
            throw new BusinessException(BusinessErrorCode.AMOUNT_OUT_OF_RANGE);
        }
        Instant now = clock.instant();
        String draftId = secure.newId();
        var payer = accounts.resolvePersonalAccount(payerUserId);
        var payee = accounts.resolvePersonalAccount(payeeUserId);
        if (!"ACTIVE".equals(payer.status()) || !"ACTIVE".equals(payee.status())) {
            throw new BusinessException(BusinessErrorCode.ACCOUNT_UNAVAILABLE);
        }
        if (payer.accountId().equals(payee.accountId())) {
            throw new BusinessException(BusinessErrorCode.SELF_PAYMENT_FORBIDDEN);
        }
        if (!store.reserveIdempotency(draftId, payerUserId, "CREATE_TRANSFER_DRAFT", idempotencyKey,
                hash, "TRANSFER_DRAFT", draftId, now)) {
            existing = store.findIdempotencyForUpdate(payerUserId, "CREATE_TRANSFER_DRAFT", idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("幂等占位冲突后未读取到既有事实"));
            if (!Arrays.equals(existing.requestHash(), hash)) conflict();
            return requiredDraft(existing.resourceId(), payerUserId);
        }
        TransferDraft draft = TransferDraft.create(draftId, payerUserId, payeeUserId,
                payer.accountId(), payee.accountId(), amountFen, remark, now);
        store.createDraft(draft);
        return draft;
    }

    /** 查询本人草稿并附带收款方脱敏展示信息；非本人统一按不存在处理。 */
    @Transactional(readOnly = true)
    public DraftView getDraft(String userId, String draftId) {
        TransferDraft draft = requiredDraft(draftId, userId);
        // 展示信息在用户中心不可用时降级为空，不影响草稿事实查询
        UserInfoPort.UserInfo payee = userInfo.findUserInfo(draft.getPayeeUserId());
        return new DraftView(draft, DisplayMasker.maskName(payee.displayName()),
                DisplayMasker.maskAccount(payee.accountNumber()));
    }

    /** 使用版本 CAS 编辑金额和备注，字段变化会使旧确认摘要失效。 */
    @Transactional
    public TransferDraft editDraft(String userId, String draftId, long version, long amountFen, String remark) {
        TransferDraft draft = requiredDraft(draftId, userId);
        try { draft.edit(version, amountFen, remark, clock.instant()); }
        catch (IllegalArgumentException invalid) { throw new BusinessException(BusinessErrorCode.AMOUNT_OUT_OF_RANGE); }
        catch (IllegalStateException conflict) { throw mapDraftState(conflict); }
        if (!store.updateDraft(draft, version)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        return draft;
    }

    /** 校验草稿并返回确定性的 PASS 结果；阶段四不隐式创建风控工单。 */
    @Transactional
    public ValidationResult validateDraft(String userId, String draftId, long version) {
        TransferDraft draft = requiredDraft(draftId, userId);
        try { draft.validate(version, clock.instant()); }
        catch (IllegalStateException conflict) { throw mapDraftState(conflict); }
        if (!store.updateDraft(draft, version)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        return new ValidationResult("PASS", "LOW", draft.getVersion());
    }

    /**
     * 校验一次性支付证明并签发两分钟确认令牌；重新签发会原子撤销旧令牌。
     *
     * <p>前端 validate 和 confirm 之间存在时间窗口，草稿版本可能因重复校验而递增。
     * 版本不匹配时重新读取草稿并重试一次，避免前端竞态导致409。</p>
     */
    @Transactional
    public IssuedConfirmation issueConfirmation(String userId, String draftId, long subjectVersion,
                                                  String paymentProof) {
        TransferDraft draft = requiredDraft(draftId, userId);
        PaymentProofPort.VerifiedProof proof = paymentProofs.verify(userId, paymentProof, "TRANSFER_CONFIRM");
        try {
            draft.awaitConfirmation(subjectVersion, clock.instant());
        } catch (IllegalStateException versionConflict) {
            // 版本不匹配可能是 validate 并发导致的，重新读取草稿重试一次
            draft = requiredDraft(draftId, userId);
            try { draft.awaitConfirmation(draft.getVersion(), clock.instant()); }
            catch (IllegalStateException conflict) { throw mapDraftState(conflict); }
            subjectVersion = draft.getVersion();
        }
        String rawToken = secure.newConfirmationToken();
        byte[] subjectHash = subjectHash(draft);
        Instant now = clock.instant();
        Confirmation confirmation = Confirmation.issue(secure.newId(), secure.digest(rawToken),
                SubjectType.TRANSFER_DRAFT, draftId, subjectHash, userId, proof.paymentProofId(),
                proof.payPasswordVersion(), now);
        store.replaceConfirmation(confirmation, subjectVersion, draft);
        return new IssuedConfirmation(rawToken, "sha256:" + java.util.HexFormat.of().formatHex(subjectHash),
                confirmation.getExpiresAt());
    }

    /**
     * 验密并一次提交转账（H5 合并提交端点专用），把客户端原本的
     * proof → confirmations → transfers 三次串行请求压缩为一次。
     *
     * <p>编排在同一本地事务内串行执行：用户中心验密并签发证明（密码错误/锁定在触碰任何
     * 本地事实前即拒绝）→ 消费证明签发确认令牌 → 复用既有受理逻辑（幂等、来源唯一、
     * Outbox 与提交后异步 TCC 语义完全不变）。</p>
     *
     * <p>安全约束：确认令牌的签发与消费仍是受理前置条件，满足“资金执行必须持有未消费
     * 确认令牌”不变量；原始支付密码只透传用户中心，不进入日志、响应或持久化。</p>
     */
    @Transactional
    public FundTransaction submitWithPassword(String userId, String draftId, long subjectVersion,
                                              String paymentPassword, String idempotencyKey, String traceId) {
        String paymentProof = paymentProofs.verifyAndIssueProof(userId, paymentPassword, "TRANSFER_CONFIRM");
        IssuedConfirmation issued = issueConfirmation(userId, draftId, subjectVersion, paymentProof);
        return submit(userId, draftId, issued.confirmationToken(), idempotencyKey, traceId);
    }

    /**
     * 原子受理普通转账；同键同参返回原交易，同键异参冲突，令牌原文不进入日志和持久化。
     */
    @Transactional
    public FundTransaction submit(String userId, String draftId, String confirmationToken,
                                  String idempotencyKey, String traceId) {
        requireKey(idempotencyKey);
        byte[] tokenDigest = secure.digest(confirmationToken);
        byte[] requestHash = secure.digest(draftId + "\n" + Base64.getEncoder().encodeToString(tokenDigest));
        TransferDraft draft = requiredDraft(draftId, userId);
        Confirmation confirmation = store.findConfirmationForUpdate(tokenDigest)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.CONFIRMATION_MISMATCH));
        // 确认行是同一次资金受理的并发串行化屏障；等待竞争事务提交后必须重新读取幂等事实。
        var repeated = store.findByIdempotency(userId, TransactionType.TRANSFER, idempotencyKey).orElse(null);
        if (repeated != null) {
            if (!Arrays.equals(repeated.requestHash(), requestHash)) conflict();
            return repeated.transaction();
        }
        var sameSource = store.findBySource(SourceType.TRANSFER_DRAFT.name(), draftId).orElse(null);
        if (sameSource != null) return sameSource.transaction();
        if (!confirmation.getPayerUserId().equals(userId) || !confirmation.getSubjectId().equals(draftId)
                || confirmation.getSubjectType() != SubjectType.TRANSFER_DRAFT) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_MISMATCH);
        }
        if (!Arrays.equals(confirmation.getSubjectHash(), subjectHash(draft))) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_STALE);
        }
        if (paymentProofs.currentPayPasswordVersion(userId) != confirmation.getPayPasswordVersion()) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_STALE);
        }
        Instant now = clock.instant();
        try { confirmation.consume(now); }
        catch (IllegalStateException invalid) {
            if (confirmation.getStatus() == ConfirmationStatus.EXPIRED) {
                throw new BusinessException(BusinessErrorCode.CONFIRMATION_EXPIRED);
            }
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_MISMATCH);
        }
        if (!store.updateConfirmation(confirmation, ConfirmationStatus.ACTIVE.name())) {
            throw new BusinessException(BusinessErrorCode.CONFIRMATION_MISMATCH);
        }
        long draftVersion = draft.getVersion();
        try { draft.submit(draftVersion, now); }
        catch (IllegalStateException invalid) { throw new BusinessException(BusinessErrorCode.CONFIRMATION_STALE); }
        if (!store.updateDraft(draft, draftVersion)) throw new BusinessException(BusinessErrorCode.VERSION_CONFLICT);
        FundTransaction transaction = FundTransaction.accept(secure.newId(), TransactionType.TRANSFER,
                SourceType.TRANSFER_DRAFT, draftId, userId, draft.getPayerAccountId(), draft.getPayeeAccountId(),
                FundingSource.BALANCE, draft.getAmountFen(), idempotencyKey, "LOW",
                traceId == null || traceId.length() != 32 ? secure.newTraceId() : traceId, now);
        store.createTransaction(transaction, requestHash, secure.newId(), now);
        // 交易已以 PROCESSING 持久化，TCC 协调异步执行以免 Seata 全局事务往返阻塞提交响应；
        // 异步失败时由恢复扫描器按既有补偿语义接管，资金安全不依赖同步执行。
        afterCommit(() -> coordinationExecutor.execute(() -> {
            try {
                coordinator.startOrResume(transaction);
            } catch (RuntimeException failure) {
                LOGGER.warn("转账 TCC 异步协调失败，等待恢复扫描器接管：transactionId={}, cause={}",
                        transaction.getTransactionId(), failure.getMessage());
            }
            contactArchive.archivePayee(userId, draft.getPayeeUserId());
        }));
        return transaction;
    }

    /**
     * 查询本人参与的普通转账。
     *
     * <p>付款发起人可直接按主单身份访问；收款人必须通过账户中心解析本人权威账户后，
     * 与交易收款账户匹配。无关用户统一返回不存在，避免泄露交易是否存在。</p>
     */
    @Transactional(readOnly = true)
    public FundTransaction getTransaction(String userId, String transactionId) {
        FundTransaction value = store.findTransaction(transactionId)
                .map(BusinessStore.FundTransactionRecord::transaction)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.TRANSACTION_NOT_FOUND));
        if (!value.getInitiatorUserId().equals(userId)) {
            String requesterAccountId = accounts.resolvePersonalAccount(userId).accountId();
            if (!value.getPayerAccountId().equals(requesterAccountId)
                    && !value.getPayeeAccountId().equals(requesterAccountId)) {
                throw new BusinessException(BusinessErrorCode.TRANSACTION_NOT_FOUND);
            }
        }
        return value;
    }

    /**
     * 查询普通转账完整展示详情；先完成参与者授权，再读取不可变来源草稿和最小用户信息。
     *
     * <p>姓名与账户号在服务边界即脱敏，明文不出应用层；用户中心不可用时展示字段降级为空。</p>
     */
    @Transactional(readOnly = true)
    public TransferDetail getTransactionDetail(String userId, String transactionId) {
        FundTransaction transaction = getTransaction(userId, transactionId);
        TransferDraft draft = store.findDraft(transaction.getSourceOrderId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.TRANSACTION_NOT_FOUND));
        UserInfoPort.UserInfo payer = userInfo.findUserInfo(draft.getPayerUserId());
        UserInfoPort.UserInfo payee = userInfo.findUserInfo(draft.getPayeeUserId());
        return new TransferDetail(transaction, draft.getPayerUserId(),
                DisplayMasker.maskName(payer.displayName()), DisplayMasker.maskAccount(payer.accountNumber()),
                draft.getPayeeUserId(), DisplayMasker.maskName(payee.displayName()),
                DisplayMasker.maskAccount(payee.accountNumber()), draft.getRemark());
    }

    /** 仅确定终态返回回执；在途和人工复核不得伪造成功。 */
    @Transactional(readOnly = true)
    public TransferReceipt getReceipt(String userId, String transactionId) {
        FundTransaction t = getTransaction(userId, transactionId);
        if (!t.getStatus().hasDefinitiveOutcome()) throw new BusinessException(BusinessErrorCode.RECEIPT_NOT_READY);
        return new TransferReceipt(t.getTransactionId(), t.getStatus().name(), t.getAmountFen(), t.getUpdatedAt());
    }

    private TransferDraft requiredDraft(String id, String userId) {
        TransferDraft draft = store.findDraft(id).orElseThrow(() -> new BusinessException(BusinessErrorCode.DRAFT_NOT_FOUND));
        if (!draft.getPayerUserId().equals(userId)) throw new BusinessException(BusinessErrorCode.DRAFT_NOT_FOUND);
        return draft;
    }
    private byte[] subjectHash(TransferDraft d) {
        return secure.digest(d.getDraftId() + "\n" + d.getPayerUserId() + "\n" + d.getPayeeUserId() + "\n"
                + d.getPayerAccountId() + "\n" + d.getPayeeAccountId() + "\n" + d.getAmountFen() + "\n"
                + text(d.getRemark()) + "\n" + d.getVersion());
    }
    private void requireKey(String key) {
        if (!keyValidator.isValid(key)) throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }
    private BusinessException mapDraftState(IllegalStateException state) {
        return state.getMessage().contains("版本")
                ? new BusinessException(BusinessErrorCode.VERSION_CONFLICT)
                : new BusinessException(BusinessErrorCode.DRAFT_NOT_EDITABLE);
    }
    private void conflict() { throw new BusinessException(BusinessErrorCode.IDEMPOTENCY_CONFLICT); }
    private static String text(String value) { return value == null ? "" : value; }
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { action.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }

    /** 草稿预校验结果。 */
    public record ValidationResult(String result, String riskLevel, long version) { }
    /** 一次性确认签发结果，原始令牌只存在于该返回对象。 */
    public record IssuedConfirmation(String confirmationToken, String subjectHash, Instant expiresAt) { }
    /** 确定终态转账回执。 */
    public record TransferReceipt(String transactionId, String status, long amountFen, Instant completedAt) { }
    /** 草稿及收款方脱敏展示投影；展示字段可空，交易事实以草稿为准。 */
    public record DraftView(TransferDraft draft, String payeeMaskedName, String payeeMaskedAccountNumber) { }
    /** 普通转账参与者与展示详情，姓名与账号均已脱敏，交易状态仍以 transaction 为唯一事实。 */
    public record TransferDetail(FundTransaction transaction, String payerUserId, String payerDisplayName,
                                 String payerMaskedAccountNumber, String payeeUserId, String payeeDisplayName,
                                 String payeeMaskedAccountNumber, String remark) { }
}
