package com.minialalipay.business.domain.recharge;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 受控模拟充值来源订单聚合。
 *
 * <p>订单固定受理时的限额策略快照和业务日，只管理渠道等待与受理前拒绝。它不写余额、账本或资金交易；
 * 阶段四端口解锁后，应用层才可调用统一交易适配器使其进入 {@link RechargeOrderStatus#PROCESSING}。</p>
 */
public final class RechargeOrder {
    private final String rechargeOrderId;
    private final String userId;
    private final String targetAccountId;
    private final long amountFen;
    private final LocalDate businessDate;
    private final String policyId;
    private final long policyVersion;
    private RechargeOrderStatus status;
    private String transactionId;
    private String rejectReasonCode;
    private long version;
    private final Instant createdAt;
    private Instant updatedAt;

    /** 创建一个已通过限额预检、等待受控模拟渠道结果的充值订单。 */
    public static RechargeOrder create(String rechargeOrderId, String userId, String targetAccountId, long amountFen,
                                       LocalDate businessDate, RechargePolicy policy, Instant now) {
        Objects.requireNonNull(policy, "充值策略不能为空").validateAmount(amountFen);
        return new RechargeOrder(rechargeOrderId, userId, targetAccountId, amountFen, businessDate,
                policy.getPolicyId(), policy.getVersion(), RechargeOrderStatus.PENDING_CHANNEL,
                null, null, 0L, now, now);
    }

    /** 从持久化事实重建充值订单。 */
    public RechargeOrder(String rechargeOrderId, String userId, String targetAccountId, long amountFen,
                         LocalDate businessDate, String policyId, long policyVersion, RechargeOrderStatus status,
                         String transactionId, String rejectReasonCode, long version, Instant createdAt, Instant updatedAt) {
        this.rechargeOrderId = required(rechargeOrderId, "充值订单 ID");
        this.userId = required(userId, "充值用户 ID");
        this.targetAccountId = required(targetAccountId, "充值目标账户 ID");
        if (amountFen < 1) throw new IllegalArgumentException("充值金额必须大于零");
        this.amountFen = amountFen;
        this.businessDate = Objects.requireNonNull(businessDate, "充值业务日期不能为空");
        this.policyId = required(policyId, "充值策略 ID");
        if (policyVersion < 0 || version < 0) throw new IllegalArgumentException("充值订单版本不得为负数");
        this.policyVersion = policyVersion;
        this.status = Objects.requireNonNull(status, "充值订单状态不能为空");
        this.transactionId = transactionId;
        this.rejectReasonCode = rejectReasonCode;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /** 记录受控模拟渠道在资金受理前的拒绝结果；重复回调保持幂等。 */
    public void rejectByChannel(long expectedVersion, String reasonCode, Instant now) {
        checkVersion(expectedVersion);
        String validReasonCode = required(reasonCode, "渠道拒绝原因码");
        if (status == RechargeOrderStatus.REJECTED && validReasonCode.equals(rejectReasonCode)) return;
        if (status != RechargeOrderStatus.PENDING_CHANNEL) throw new IllegalStateException("充值订单当前不可记录渠道拒绝");
        status = RechargeOrderStatus.REJECTED;
        rejectReasonCode = validReasonCode;
        advance(now);
    }

    /**
     * 记录统一资金交易已经受理。
     *
     * <p>只能从待渠道状态进入处理中并绑定唯一交易主单；方法本身绝不发布成功，余额与账本由账户中心 TCC 分支核验。
     * 充值付款对手为系统虚拟发行权益科目，需要账户中心提供充值 TCC 参与者后才能真正入账。</p>
     */
    public void acceptFundTransaction(long expectedVersion, String transactionId, Instant now) {
        checkVersion(expectedVersion);
        if (status != RechargeOrderStatus.PENDING_CHANNEL) throw new IllegalStateException("充值订单当前不可受理资金交易");
        this.transactionId = required(transactionId, "统一交易 ID");
        status = RechargeOrderStatus.PROCESSING;
        advance(now);
    }

    private void checkVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("充值订单版本已经变化");
    }
    private void advance(Instant now) { version++; updatedAt = now; }
    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getRechargeOrderId() { return rechargeOrderId; }
    public String getUserId() { return userId; }
    public String getTargetAccountId() { return targetAccountId; }
    public long getAmountFen() { return amountFen; }
    public LocalDate getBusinessDate() { return businessDate; }
    public String getPolicyId() { return policyId; }
    public long getPolicyVersion() { return policyVersion; }
    public RechargeOrderStatus getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public String getRejectReasonCode() { return rejectReasonCode; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
