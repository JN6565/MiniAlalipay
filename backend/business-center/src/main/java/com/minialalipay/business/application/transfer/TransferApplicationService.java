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
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 * 主动转账草稿、确认和统一交易应用服务。
 *
 * <p>账户归属只从账户中心解析。提交事务在 business_db 内原子消费确认、推进草稿、
 * 创建主单和 Outbox；TCC 仅在本地事务提交后启动。</p>
 */
@Service
public class TransferApplicationService {
    private final BusinessStore store;
    private final AccountDirectoryPort accounts;
    private final PaymentProofPort paymentProofs;
    private final TccCoordinatorPort coordinator;
    private final SecurityMaterialPort secure;
    private final IdempotencyKeyValidator keyValidator;
    private final ContactArchivePort contactArchive;
    private final UserInfoPort userInfo;
    private final Clock clock;

    @Autowired
    public TransferApplicationService(BusinessStore store, AccountDirectoryPort accounts,
                                      PaymentProofPort paymentProofs, TccCoordinatorPort coordinator,
                                      SecurityMaterialPort secure, IdempotencyKeyValidator keyValidator,
                                      ContactArchivePort contactArchive, UserInfoPort userInfo) {
        this(store, accounts, paymentProofs, coordinator, secure, keyValidator, contactArchive, userInfo,
                Clock.systemUTC());
    }
    TransferApplicationService(BusinessStore store, AccountDirectoryPort accounts,
                               PaymentProofPort paymentProofs, TccCoordinatorPort coordinator,
                               SecurityMaterialPort secure, IdempotencyKeyValidator keyValidator,
                               ContactArchivePort contactArchive, UserInfoPort userInfo, Clock clock) {
        this.store = store; this.accounts = accounts; this.paymentProofs = paymentProofs;
        this.coordinator = coordinator; this.secure = secure; this.keyValidator = keyValidator;
        this.contactArchive = contactArchive; this.clock = clock;
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

    /** 查询本人草稿；非本人统一按不存在处理。 */
    @Transactional(readOnly = true)
    public TransferDraft getDraft(String userId, String draftId) { return requiredDraft(draftId, userId); }

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

    /** 校验一次性支付证明并签发两分钟确认令牌；重新签发会原子撤销旧令牌。 */
    @Transactional
    public IssuedConfirmation issueConfirmation(String userId, String draftId, long subjectVersion,
                                                  String paymentProof) {
        TransferDraft draft = requiredDraft(draftId, userId);
        PaymentProofPort.VerifiedProof proof = paymentProofs.verify(userId, paymentProof, "TRANSFER_CONFIRM");
        try { draft.awaitConfirmation(subjectVersion, clock.instant()); }
        catch (IllegalStateException conflict) { throw mapDraftState(conflict); }
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
        afterCommit(() -> {
            coordinator.startOrResume(transaction);
            contactArchive.archivePayee(userId, draft.getPayeeUserId());
        });
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
     */
    @Transactional(readOnly = true)
    public TransferDetail getTransactionDetail(String userId, String transactionId) {
        FundTransaction transaction = getTransaction(userId, transactionId);
        TransferDraft draft = store.findDraft(transaction.getSourceOrderId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.TRANSACTION_NOT_FOUND));
        String payerDisplayName = userInfo.findUserInfo(draft.getPayerUserId()).displayName();
        String payeeDisplayName = userInfo.findUserInfo(draft.getPayeeUserId()).displayName();
        return new TransferDetail(transaction, draft.getPayerUserId(), payerDisplayName,
                draft.getPayeeUserId(), payeeDisplayName, draft.getRemark());
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
    /** 普通转账参与者与展示详情，交易状态仍以 transaction 为唯一事实。 */
    public record TransferDetail(FundTransaction transaction, String payerUserId, String payerDisplayName,
                                 String payeeUserId, String payeeDisplayName, String remark) { }
}
