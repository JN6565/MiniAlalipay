package com.minialalipay.business.application.port;

import com.minialalipay.business.domain.confirmation.Confirmation;
import com.minialalipay.business.domain.transaction.FundTransaction;
import com.minialalipay.business.domain.transaction.TransactionStatus;
import com.minialalipay.business.domain.transaction.TransactionType;
import com.minialalipay.business.domain.transfer.TransferDraft;
import com.minialalipay.business.domain.qrpay.QrPayOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 业务中心本地事实仓储端口；写方法由应用层 business_db 本地事务编排。 */
public interface BusinessStore {
    /** 原子抢占写接口幂等键；返回 false 表示同一作用域内已有记录。 */
    boolean reserveIdempotency(String recordId, String principalId, String operation, String key,
                               byte[] requestHash, String resourceType, String resourceId, Instant now);
    /** 在当前 business_db 事务中创建草稿并完成对应幂等记录。 */
    void createDraft(TransferDraft draft);
    Optional<IdempotencyRecord> findIdempotency(String principalId, String operation, String key);
    Optional<IdempotencyRecord> findIdempotencyForUpdate(String principalId, String operation, String key);
    Optional<TransferDraft> findDraft(String draftId);
    boolean updateDraft(TransferDraft draft, long expectedVersion);
    void replaceConfirmation(Confirmation confirmation, long draftExpectedVersion, TransferDraft draft);
    /**
     * 原子撤销同一二维码订单旧确认、写入新确认并以订单版本 CAS 锁定付款人。
     *
     * <p>确认与订单更新必须同属 business_db 本地事务，避免旧确认可消费而付款人或订单版本尚未落库。</p>
     */
    void replaceQrPayConfirmation(Confirmation confirmation, long orderExpectedVersion, QrPayOrder order);
    /**
     * 原子撤销同一 C2C 订单的旧确认并写入新确认。
     *
     * <p>订单付款人已在令牌交换阶段由服务端身份派生，因此确认替换无需再次修改订单身份字段。</p>
     */
    void replaceCollectionConfirmation(Confirmation confirmation);
    Optional<Confirmation> findConfirmationForUpdate(byte[] tokenDigest);
    boolean updateConfirmation(Confirmation confirmation, String expectedStatus);
    Optional<FundTransactionRecord> findByIdempotency(String userId, TransactionType type, String key);
    Optional<FundTransactionRecord> findBySource(String sourceType, String sourceId);
    Optional<FundTransactionRecord> findTransaction(String transactionId);
    void createTransaction(FundTransaction transaction, byte[] requestHash, String eventId, Instant now);
    boolean updateTransaction(FundTransaction transaction, long expectedVersion, String eventId, Instant now);
    void createTccGlobal(String xid, String transactionId, Instant now);
    void updateTccGlobal(String xid, String status, String branchSummary, Instant nextRetryAt, Instant now);
    /** 原子提交交易终态、全局 TCC 状态和 Outbox；CAS 失败返回 false。 */
    boolean finalizeTransaction(FundTransaction transaction, long expectedVersion, String xid,
                                String globalStatus, String eventId, Instant now);
    /** 原子提交人工态、全局 TCC 状态、Outbox 和活动人工工单；CAS 失败返回 false。 */
    boolean moveToManualReview(FundTransaction transaction, long expectedVersion, String xid,
                               String eventId, String caseId, String reasonCode, Instant now);
    List<FundTransactionRecord> findRecoverable(Instant updatedBefore, int limit);

    /** 交易与请求摘要的持久化查询结果。 */
    record FundTransactionRecord(FundTransaction transaction, byte[] requestHash) { }
    /** 通用创建接口幂等事实。 */
    record IdempotencyRecord(byte[] requestHash, String resourceId) { }
}
