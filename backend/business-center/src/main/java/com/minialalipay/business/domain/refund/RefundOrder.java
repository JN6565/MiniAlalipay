package com.minialalipay.business.domain.refund;

import java.time.Instant;
import java.util.Objects;

/**
 * 受控退款来源订单聚合。
 *
 * <p>对已成功的动态扫码收款（原收款方为订单持有者）发起受控全额虚拟退款。
 * 订单固定受理时的原交易事实快照（原交易、商户/付款双方、业务类型、资金来源、金额与原因），
 * 只管理创建与提交推进；不写余额、账本或资金交易。资金执行复用统一 REFUND 交易与 TCC，
 * 账户中心尚未提供退款 TCC 参与者前，受理会安全失败并转入人工处置，绝不伪造冲正。</p>
 */
public final class RefundOrder {
    private final String refundOrderId;
    private final String originalTransactionId;
    /** 退款发起人（原收款方）用户标识。 */
    private final String merchantUserId;
    /** 退款发起人账户，REFUND 交易付款方。 */
    private final String merchantAccountId;
    /** 原付款人用户标识。 */
    private final String payerUserId;
    /** 原付款人账户，REFUND 交易收款方。 */
    private final String payerAccountId;
    /** 原交易业务类型，QR_PAY 或 CREDIT_PAY。 */
    private final String originalBusinessType;
    /** 原交易资金来源，BALANCE 或 MINI_CREDIT。 */
    private final String fundingSource;
    private final long amountFen;
    /** 退款原因，由退款发起人填写。 */
    private final String reasonCode;
    private RefundOrderStatus status;
    private String transactionId;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;

    /** 创建一份已校验原交易的待提交退款订单。 */
    public static RefundOrder create(String refundOrderId, String originalTransactionId,
                                     String merchantUserId, String merchantAccountId, String payerUserId,
                                     String payerAccountId, String originalBusinessType, String fundingSource,
                                     long amountFen, String reasonCode, Instant now) {
        if (amountFen < 1) throw new IllegalArgumentException("退款金额必须大于零");
        return new RefundOrder(refundOrderId, originalTransactionId, merchantUserId, merchantAccountId,
                payerUserId, payerAccountId, originalBusinessType, fundingSource, amountFen, reasonCode,
                RefundOrderStatus.CREATED, null, 0L, now, now, null);
    }

    /** 从持久化事实重建退款订单。 */
    public RefundOrder(String refundOrderId, String originalTransactionId, String merchantUserId,
                       String merchantAccountId, String payerUserId, String payerAccountId,
                       String originalBusinessType, String fundingSource, long amountFen, String reasonCode,
                       RefundOrderStatus status, String transactionId, long version,
                       Instant createdAt, Instant updatedAt, Instant completedAt) {
        this.refundOrderId = required(refundOrderId, "退款订单 ID");
        this.originalTransactionId = required(originalTransactionId, "原交易 ID");
        this.merchantUserId = required(merchantUserId, "退款发起人 ID");
        this.merchantAccountId = required(merchantAccountId, "退款发起人账户 ID");
        this.payerUserId = required(payerUserId, "原付款人 ID");
        this.payerAccountId = required(payerAccountId, "原付款人账户 ID");
        this.originalBusinessType = required(originalBusinessType, "原交易业务类型");
        this.fundingSource = required(fundingSource, "原交易资金来源");
        if (amountFen < 1) throw new IllegalArgumentException("退款金额必须大于零");
        this.amountFen = amountFen;
        this.reasonCode = required(reasonCode, "退款原因");
        this.status = Objects.requireNonNull(status, "退款订单状态不能为空");
        this.transactionId = transactionId;
        if (version < 0) throw new IllegalArgumentException("退款订单版本不得为负数");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
        this.completedAt = completedAt;
    }

    /**
     * 提交执行退款并绑定唯一 REFUND 交易。
     *
     * <p>只能从创建状态进入处理中；方法本身绝不发布成功，冲正由账户中心 TCC 分支核验。
     * 账户中心尚未提供退款 TCC 参与者前，受理会安全失败并转入人工处置。</p>
     */
    public void submit(long expectedVersion, String transactionId, Instant now) {
        checkVersion(expectedVersion);
        if (status != RefundOrderStatus.CREATED) throw new IllegalStateException("退款订单当前不可提交执行");
        this.transactionId = required(transactionId, "统一交易 ID");
        status = RefundOrderStatus.PROCESSING;
        advance(now);
    }

    private void checkVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("退款订单版本已经变化");
    }
    private void advance(Instant now) { version++; updatedAt = now; }
    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getRefundOrderId() { return refundOrderId; }
    public String getOriginalTransactionId() { return originalTransactionId; }
    public String getMerchantUserId() { return merchantUserId; }
    public String getMerchantAccountId() { return merchantAccountId; }
    public String getPayerUserId() { return payerUserId; }
    public String getPayerAccountId() { return payerAccountId; }
    public String getOriginalBusinessType() { return originalBusinessType; }
    public String getFundingSource() { return fundingSource; }
    public long getAmountFen() { return amountFen; }
    public String getReasonCode() { return reasonCode; }
    public RefundOrderStatus getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
