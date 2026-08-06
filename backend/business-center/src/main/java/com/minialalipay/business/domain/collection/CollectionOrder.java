package com.minialalipay.business.domain.collection;

import java.time.Instant;
import java.time.Duration;
import java.util.Objects;

/**
 * 个人收款码或固定收款请求产生的 C2C 来源订单。
 *
 * <p>订单仅锁定场景事实和付款竞争结果。它不创建交易、不冻结资金；后续必须由统一交易端口在
 * 受理成功后填充 {@code PROCESSING}，领域层不能将该订单置为成功。</p>
 */
public final class CollectionOrder {
    /** C2C 付款订单在未受理资金前最多保留三十分钟。 */
    private static final Duration VALIDITY = Duration.ofMinutes(30);
    private final String orderId;
    private final String personalCodeId;
    private final String requestId;
    private final String payeeUserId;
    private final String payeeAccountId;
    private final String payerUserId;
    private final String payerAccountId;
    private Long amountFen;
    private String subject;
    private CollectionOrderStatus status;
    private String transactionId;
    private long version;
    private final Instant createdAt;
    private final Instant expiresAt;
    private Instant updatedAt;

    /** 为个人收款码创建待填写金额的订单。 */
    public static CollectionOrder forPersonalCode(String orderId, String personalCodeId, String payeeUserId,
                                                   String payeeAccountId, String payerUserId, String payerAccountId,
                                                   Instant now) {
        return new CollectionOrder(orderId, personalCodeId, null, payeeUserId, payeeAccountId,
                payerUserId, payerAccountId, null, null, CollectionOrderStatus.DRAFT, 0L, now, now);
    }

    /** 为固定收款请求创建金额已锁定的待确认订单。 */
    public static CollectionOrder forFixedRequest(String orderId, String requestId, String payeeUserId,
                                                   String payeeAccountId, String payerUserId, String payerAccountId,
                                                   long amountFen, String subject, Instant now) {
        return new CollectionOrder(orderId, null, requestId, payeeUserId, payeeAccountId, payerUserId,
                payerAccountId, amountFen, subject, CollectionOrderStatus.PENDING_CONFIRMATION, 0L, now, now);
    }

    /** 从持久化事实重建 C2C 来源订单。 */
    public CollectionOrder(String orderId, String personalCodeId, String requestId, String payeeUserId,
                           String payeeAccountId, String payerUserId, String payerAccountId, Long amountFen,
                           String subject, CollectionOrderStatus status, long version, Instant createdAt, Instant updatedAt) {
        this(orderId, personalCodeId, requestId, payeeUserId, payeeAccountId, payerUserId, payerAccountId,
                amountFen, subject, status, null, version, createdAt, createdAt.plus(VALIDITY), updatedAt);
    }

    /**
     * 从持久化事实重建 C2C 来源订单。
     *
     * <p>过期时间必须使用数据库保存的值，避免迁移、恢复或规则调整后按创建时间重新推导而改变既有订单的有效期。</p>
     */
    public CollectionOrder(String orderId, String personalCodeId, String requestId, String payeeUserId,
                           String payeeAccountId, String payerUserId, String payerAccountId, Long amountFen,
                           String subject, CollectionOrderStatus status, long version, Instant createdAt,
                           Instant expiresAt, Instant updatedAt) {
        this(orderId, personalCodeId, requestId, payeeUserId, payeeAccountId, payerUserId, payerAccountId,
                amountFen, subject, status, null, version, createdAt, expiresAt, updatedAt);
    }

    /** 从持久化事实重建含统一交易关联的 C2C 来源订单。 */
    public CollectionOrder(String orderId, String personalCodeId, String requestId, String payeeUserId,
                           String payeeAccountId, String payerUserId, String payerAccountId, Long amountFen,
                           String subject, CollectionOrderStatus status, String transactionId, long version,
                           Instant createdAt, Instant expiresAt, Instant updatedAt) {
        this.orderId = required(orderId, "C2C 订单 ID");
        if ((personalCodeId == null) == (requestId == null)) throw new IllegalArgumentException("C2C 订单必须且只能关联一种收款来源");
        this.personalCodeId = personalCodeId;
        this.requestId = requestId;
        this.payeeUserId = required(payeeUserId, "收款用户 ID");
        this.payeeAccountId = required(payeeAccountId, "收款账户 ID");
        this.payerUserId = required(payerUserId, "付款用户 ID");
        this.payerAccountId = required(payerAccountId, "付款账户 ID");
        if (this.payeeUserId.equals(this.payerUserId) || this.payeeAccountId.equals(this.payerAccountId)) {
            throw new IllegalArgumentException("不允许向本人付款");
        }
        if (amountFen != null && amountFen < 1) throw new IllegalArgumentException("收款金额必须大于零");
        this.amountFen = amountFen;
        this.subject = subject;
        this.transactionId = transactionId;
        this.status = Objects.requireNonNull(status, "C2C 订单状态不能为空");
        if (version < 0) throw new IllegalArgumentException("C2C 订单版本不得为负数");
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.expiresAt = Objects.requireNonNull(expiresAt, "订单过期时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /** 仅付款人可调用一次，为个人收款码订单锁定金额和主题。 */
    public void lockPersonalAmount(String actorUserId, long expectedVersion, long amountFen, String subject, Instant now) {
        if (version != expectedVersion) throw new IllegalStateException("C2C 订单版本已经变化");
        if (personalCodeId == null) throw new IllegalStateException("固定收款请求金额不可修改");
        if (!payerUserId.equals(actorUserId)) throw new IllegalStateException("仅绑定付款人可以锁定个人码订单金额");
        if (status != CollectionOrderStatus.DRAFT) throw new IllegalStateException("个人码订单当前不可修改金额");
        if (amountFen < 1) throw new IllegalArgumentException("收款金额必须大于零");
        this.amountFen = amountFen;
        this.subject = required(subject, "收款主题");
        status = CollectionOrderStatus.PENDING_CONFIRMATION;
        version++;
        updatedAt = now;
    }

    /** 将待确认订单转入人工风控复核；不创建交易或冻结资金。 */
    public void markRiskReview(long expectedVersion, Instant now) {
        if (version != expectedVersion) throw new IllegalStateException("C2C 订单版本已经变化");
        if (status != CollectionOrderStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("C2C 订单当前不可转人工风控复核");
        }
        status = CollectionOrderStatus.RISK_REVIEW;
        version++;
        updatedAt = Objects.requireNonNull(now, "复核时间不能为空");
    }

    /** 运营批准人工复核后将订单恢复到待确认，用户可重新确认；仅 RISK_REVIEW 可恢复。 */
    public void resumeFromRiskReview(long expectedVersion, Instant now) {
        if (version != expectedVersion) throw new IllegalStateException("C2C 订单版本已经变化");
        if (status != CollectionOrderStatus.RISK_REVIEW) {
            throw new IllegalStateException("C2C 订单当前不在人工复核状态");
        }
        status = CollectionOrderStatus.PENDING_CONFIRMATION;
        version++;
        updatedAt = Objects.requireNonNull(now, "恢复时间不能为空");
    }

    /**
     * 将已完成确认的订单受理到唯一的统一资金交易。
     *
     * <p>这里只能进入处理中并关联交易主单；成功、取消和人工终态只能由交易终态发布器依据资金事实回填。</p>
     */
    public void acceptByFundTransaction(long expectedVersion, String value, Instant now) {
        if (version != expectedVersion) throw new IllegalStateException("C2C 订单版本已经变化");
        if (status != CollectionOrderStatus.PENDING_CONFIRMATION || amountFen == null) {
            throw new IllegalStateException("C2C 订单当前不可受理资金");
        }
        transactionId = required(value, "统一交易 ID");
        status = CollectionOrderStatus.PROCESSING;
        version++;
        updatedAt = Objects.requireNonNull(now, "受理时间不能为空");
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getOrderId() { return orderId; }
    public String getPersonalCodeId() { return personalCodeId; }
    public String getRequestId() { return requestId; }
    public String getPayeeUserId() { return payeeUserId; }
    public String getPayeeAccountId() { return payeeAccountId; }
    public String getPayerUserId() { return payerUserId; }
    public String getPayerAccountId() { return payerAccountId; }
    public Long getAmountFen() { return amountFen; }
    public String getSubject() { return subject; }
    public CollectionOrderStatus getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    /** 返回未受理资金前的订单有效期。 */
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
