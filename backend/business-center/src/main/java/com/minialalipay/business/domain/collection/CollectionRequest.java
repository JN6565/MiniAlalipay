package com.minialalipay.business.domain.collection;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 固定金额 C2C 收款请求聚合。
 *
 * <p>金额和主题自创建起不可变。一码多收：有效期内多个 H5 会话、多个付款人可各自新建订单，
 * 支付受理时只校验请求仍可收款（未过期、未取消），多笔订单独立受理，不做单笔占用。</p>
 */
public final class CollectionRequest {
    /** 固定收款请求的有效期。 */
    public static final Duration VALIDITY = Duration.ofMinutes(30);

    private final String requestId;
    private final String payeeUserId;
    private final String payeeAccountId;
    private final long amountFen;
    private final String subject;
    private final Instant createdAt;
    private final Instant expiresAt;
    private CollectionRequestStatus status;
    private String activeOrderId;
    private long version;
    private Instant updatedAt;

    /** 创建金额、主题不可变的固定收款请求。 */
    public static CollectionRequest create(String requestId, String payeeUserId, String payeeAccountId,
                                           long amountFen, String subject, Instant now) {
        return new CollectionRequest(requestId, payeeUserId, payeeAccountId, amountFen, subject,
                CollectionRequestStatus.OPEN, null, 0L, now.plus(VALIDITY), now, now);
    }

    /** 从持久化事实重建固定收款请求。 */
    public CollectionRequest(String requestId, String payeeUserId, String payeeAccountId, long amountFen,
                             String subject, CollectionRequestStatus status, String activeOrderId, long version,
                             Instant expiresAt, Instant createdAt, Instant updatedAt) {
        this.requestId = required(requestId, "固定收款请求 ID");
        this.payeeUserId = required(payeeUserId, "收款用户 ID");
        this.payeeAccountId = required(payeeAccountId, "收款账户 ID");
        if (amountFen < 1) throw new IllegalArgumentException("固定收款金额必须大于零");
        this.amountFen = amountFen;
        this.subject = required(subject, "收款主题");
        this.status = Objects.requireNonNull(status, "固定收款请求状态不能为空");
        this.activeOrderId = activeOrderId;
        if (version < 0) throw new IllegalArgumentException("固定收款请求版本不得为负数");
        this.version = version;
        this.expiresAt = Objects.requireNonNull(expiresAt, "过期时间不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /**
     * 收款方主动关闭尚未有支付受理的请求。
     *
     * <p>一码多收模型下只有 {@link CollectionRequestStatus#OPEN} 表示还没有任何一笔进入资金受理，
     * 一旦有订单受理（PROCESSING）请求必须保留至全部订单终态，禁止关闭。</p>
     */
    public void close(long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        if (expireIfNecessary(now) || status == CollectionRequestStatus.EXPIRED) {
            throw new IllegalStateException("固定收款请求已过期");
        }
        if (status == CollectionRequestStatus.CANCELLED) return;
        if (status != CollectionRequestStatus.OPEN) throw new IllegalStateException("固定收款请求当前不可关闭");
        status = CollectionRequestStatus.CANCELLED;
        version++;
        updatedAt = now;
    }

    private void checkVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("固定收款请求版本已经变化");
    }

    /**
     * 将未进入资金受理的超时请求转为过期终态。
     *
     * <p>应用层必须以转换前版本执行 CAS 持久化；仅抛出过期异常会导致事务回滚而丢失过期事实。</p>
     * @return 是否发生状态变更
     */
    public boolean expireIfNecessary(Instant now) {
        if (!now.isBefore(expiresAt) && (status == CollectionRequestStatus.OPEN || status == CollectionRequestStatus.RESERVED)) {
            status = CollectionRequestStatus.EXPIRED;
            version++;
            updatedAt = now;
            return true;
        }
        return false;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value;
    }

    public String getRequestId() { return requestId; }
    public String getPayeeUserId() { return payeeUserId; }
    public String getPayeeAccountId() { return payeeAccountId; }
    public long getAmountFen() { return amountFen; }
    public String getSubject() { return subject; }
    public CollectionRequestStatus getStatus() { return status; }
    public String getActiveOrderId() { return activeOrderId; }
    public long getVersion() { return version; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
