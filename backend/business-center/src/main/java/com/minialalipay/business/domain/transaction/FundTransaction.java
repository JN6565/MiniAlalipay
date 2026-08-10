package com.minialalipay.business.domain.transaction;

import java.time.Instant;
import java.util.Objects;

/** 统一资金交易主单，只有终态发布器完成事实核验后才能进入确定终态。 */
public final class FundTransaction {
    private final String transactionId;
    private final TransactionType businessType;
    private final SourceType sourceType;
    private final String sourceOrderId;
    private final String initiatorUserId;
    private final String payerAccountId;
    private final String payeeAccountId;
    private final FundingSource fundingSource;
    private final String relatedTransactionId;
    private final long amountFen;
    private final String idempotencyKey;
    private TransactionStatus status;
    private final String riskLevel;
    private final String traceId;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /** 受理一笔普通转账，初始状态固定为 PROCESSING。 */
    public static FundTransaction accept(String transactionId, TransactionType businessType, SourceType sourceType,
                                         String sourceOrderId, String initiatorUserId, String payerAccountId,
                                         String payeeAccountId, FundingSource fundingSource, long amountFen,
                                         String idempotencyKey, String riskLevel, String traceId, Instant now) {
        return new FundTransaction(transactionId, businessType, sourceType, sourceOrderId, initiatorUserId,
                payerAccountId, payeeAccountId, fundingSource, amountFen, idempotencyKey,
                TransactionStatus.PROCESSING, riskLevel, traceId, 0L, now, now, null);
    }

    /** 受理一笔关联原支付交易的受控退款，初始状态固定为 PROCESSING。 */
    public static FundTransaction acceptRefund(String transactionId, SourceType sourceType, String sourceOrderId,
                                               String initiatorUserId, String payerAccountId, String payeeAccountId,
                                               FundingSource fundingSource, long amountFen, String idempotencyKey,
                                               String riskLevel, String traceId, String relatedTransactionId, Instant now) {
        return new FundTransaction(transactionId, TransactionType.REFUND, sourceType, sourceOrderId, initiatorUserId,
                payerAccountId, payeeAccountId, fundingSource, amountFen, idempotencyKey,
                TransactionStatus.PROCESSING, riskLevel, traceId, 0L, now, now, relatedTransactionId);
    }

    /** 从持久化事实重建交易；退款等场景通过关联字段带回原支付交易号。 */
    public FundTransaction(String transactionId, TransactionType businessType, SourceType sourceType,
                           String sourceOrderId, String initiatorUserId, String payerAccountId,
                           String payeeAccountId, FundingSource fundingSource, long amountFen,
                           String idempotencyKey, TransactionStatus status, String riskLevel,
                           String traceId, long version, Instant createdAt, Instant updatedAt) {
        this(transactionId, businessType, sourceType, sourceOrderId, initiatorUserId, payerAccountId,
                payeeAccountId, fundingSource, amountFen, idempotencyKey, status, riskLevel,
                traceId, version, createdAt, updatedAt, null);
    }

    /** 规范化全参数重建构造器，支持退款关联原支付交易。 */
    public FundTransaction(String transactionId, TransactionType businessType, SourceType sourceType,
                           String sourceOrderId, String initiatorUserId, String payerAccountId,
                           String payeeAccountId, FundingSource fundingSource, long amountFen,
                           String idempotencyKey, TransactionStatus status, String riskLevel,
                           String traceId, long version, Instant createdAt, Instant updatedAt,
                           String relatedTransactionId) {
        this.transactionId = required(transactionId); this.businessType = Objects.requireNonNull(businessType);
        this.sourceType = Objects.requireNonNull(sourceType); this.sourceOrderId = required(sourceOrderId);
        this.initiatorUserId = required(initiatorUserId);
        if (businessType == TransactionType.RECHARGE) {
            if (payerAccountId != null && !payerAccountId.isBlank()) {
                throw new IllegalArgumentException("充值交易不得指定付款账户");
            }
            if (fundingSource != FundingSource.SYSTEM_ISSUANCE) {
                throw new IllegalArgumentException("充值交易必须使用系统发行资金");
            }
            this.payerAccountId = null;
        } else {
            this.payerAccountId = required(payerAccountId);
        }
        this.payeeAccountId = required(payeeAccountId); this.fundingSource = Objects.requireNonNull(fundingSource);
        if (payerAccountId != null && payerAccountId.equals(payeeAccountId)) throw new IllegalArgumentException("付款和收款账户不能相同");
        if (amountFen < 1 || amountFen > 5_000_000L) throw new IllegalArgumentException("金额超出允许范围");
        this.amountFen = amountFen; this.idempotencyKey = required(idempotencyKey);
        this.status = Objects.requireNonNull(status); this.riskLevel = required(riskLevel);
        if (traceId == null || traceId.length() != 32) throw new IllegalArgumentException("链路编号必须为 32 位");
        this.traceId = traceId;
        if (version < 0) throw new IllegalArgumentException("交易版本不得为负");
        this.version = version; this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.relatedTransactionId = relatedTransactionId;
    }

    /** 开始取消或补偿。 */
    public void startCompensating(Instant now) { transition(TransactionStatus.PROCESSING, TransactionStatus.COMPENSATING, now); }
    /** 事实完全一致时发布成功。 */
    public void publishSuccess(boolean verified, Instant now) {
        if (!verified) throw new IllegalStateException("资金和账本事实尚未核验一致");
        transition(TransactionStatus.PROCESSING, TransactionStatus.SUCCESS, now);
    }
    /** 所有取消事实一致时发布取消。 */
    public void publishCancelled(boolean verified, Instant now) {
        if (!verified) throw new IllegalStateException("取消事实尚未核验一致");
        transition(TransactionStatus.COMPENSATING, TransactionStatus.CANCELLED, now);
    }
    /** 未知结果转入人工复核，保留资金事实等待处置。 */
    public void requireManualReview(Instant now) {
        if (status != TransactionStatus.PROCESSING && status != TransactionStatus.COMPENSATING) {
            throw new IllegalStateException("当前交易状态不能转人工复核");
        }
        status = TransactionStatus.MANUAL_REVIEW; version++; updatedAt = now;
    }
    /**
     * 人工态交易转回在途态，供恢复任务用原稳定分支键重新驱动 TCC 并重新核验资金事实。
     *
     * <p>系统分析状态机允许 {@code MANUAL_REVIEW -> PROCESSING}。按工单原因选择恢复路径：
     * 取消事实不一致（{@code cancelPath=true}）转回 {@link TransactionStatus#COMPENSATING} 继续补偿；
     * 其余原因（成功事实不一致或差异写入失败）转回 {@link TransactionStatus#PROCESSING} 正向完成。
     * 事实仍不一致时协调器会再次将其转回人工态，不影响真实异常场景的人工处置。</p>
     *
     * @param cancelPath 是否按取消补偿路径恢复
     * @param now 状态变更时间
     */
    public void resumeFromManualReview(boolean cancelPath, Instant now) {
        if (status != TransactionStatus.MANUAL_REVIEW) {
            throw new IllegalStateException("仅人工复核中的交易可以重新核验");
        }
        status = cancelPath ? TransactionStatus.COMPENSATING : TransactionStatus.PROCESSING;
        version++; updatedAt = Objects.requireNonNull(now);
    }
    private void transition(TransactionStatus expected, TransactionStatus target, Instant now) {
        if (status != expected) throw new IllegalStateException("交易状态不允许该操作");
        status = target; version++; updatedAt = Objects.requireNonNull(now);
    }
    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("交易字段不能为空");
        return value;
    }
    public String getTransactionId() { return transactionId; }
    public TransactionType getBusinessType() { return businessType; }
    public SourceType getSourceType() { return sourceType; }
    public String getSourceOrderId() { return sourceOrderId; }
    public String getInitiatorUserId() { return initiatorUserId; }
    public String getPayerAccountId() { return payerAccountId; }
    public String getPayeeAccountId() { return payeeAccountId; }
    public FundingSource getFundingSource() { return fundingSource; }
    public String getRelatedTransactionId() { return relatedTransactionId; }
    public long getAmountFen() { return amountFen; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public TransactionStatus getStatus() { return status; }
    public String getRiskLevel() { return riskLevel; }
    public String getTraceId() { return traceId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
