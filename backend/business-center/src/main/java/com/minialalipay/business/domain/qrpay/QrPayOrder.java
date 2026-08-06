package com.minialalipay.business.domain.qrpay;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 动态扫码收款来源订单聚合。
 *
 * <p>该聚合仅管理二维码令牌、扫描会话、付款方锁定和受理前风控分流。它不创建资金交易、
 * 不操作余额或账本，也不自行发布支付成功；阶段四解锁后，应用层才可通过统一交易端口调用
 * {@link #acceptByFundTransaction(long, String, Instant)}。</p>
 */
public final class QrPayOrder {
    /** 动态二维码的有效期。 */
    public static final Duration VALIDITY = Duration.ofMinutes(5);

    private final String orderId;
    private final String payeeUserId;
    private final String payeeAccountId;
    private final long amountFen;
    private final String subject;
    private final QrTokenDigest tokenDigest;
    private final Instant createdAt;
    private final Instant expiresAt;
    private QrPayOrderStatus status;
    private String boundBootstrapSessionId;
    private String payerUserId;
    private String payerAccountId;
    private String transactionId;
    private long version;
    private Instant updatedAt;

    /**
     * 创建一个尚未被 H5 会话交换的二维码订单。
     *
     * @param orderId 来源订单标识
     * @param payeeUserId 从已认证会话解析的收款用户标识
     * @param payeeAccountId 从已认证会话解析的收款账户标识
     * @param amountFen 收款金额，单位为分
     * @param subject 收款主题
     * @param tokenDigest 原始令牌的 SHA-256 摘要
     * @param now 创建时间
     * @return 新建订单
     */
    public static QrPayOrder create(String orderId, String payeeUserId, String payeeAccountId,
                                    long amountFen, String subject, QrTokenDigest tokenDigest, Instant now) {
        return new QrPayOrder(orderId, payeeUserId, payeeAccountId, amountFen, subject, tokenDigest,
                QrPayOrderStatus.CREATED, null, null, null, null, 0L, now.plus(VALIDITY), now, now);
    }

    /** 从持久化事实重建订单。 */
    public QrPayOrder(String orderId, String payeeUserId, String payeeAccountId, long amountFen,
                      String subject, QrTokenDigest tokenDigest, QrPayOrderStatus status,
                      String boundBootstrapSessionId, String payerUserId, String payerAccountId,
                      String transactionId, long version, Instant expiresAt, Instant createdAt, Instant updatedAt) {
        this.orderId = required(orderId, "二维码订单 ID");
        this.payeeUserId = required(payeeUserId, "收款用户 ID");
        this.payeeAccountId = required(payeeAccountId, "收款账户 ID");
        if (amountFen < 1) {
            throw new IllegalArgumentException("收款金额必须大于零");
        }
        this.amountFen = amountFen;
        this.subject = required(subject, "收款主题");
        this.tokenDigest = Objects.requireNonNull(tokenDigest, "二维码令牌摘要不能为空");
        this.status = Objects.requireNonNull(status, "二维码订单状态不能为空");
        this.boundBootstrapSessionId = boundBootstrapSessionId;
        this.payerUserId = payerUserId;
        this.payerAccountId = payerAccountId;
        this.transactionId = transactionId;
        if (version < 0) {
            throw new IllegalArgumentException("二维码订单版本不得为负数");
        }
        this.version = version;
        this.expiresAt = Objects.requireNonNull(expiresAt, "过期时间不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /**
     * 受保护地交换二维码令牌并绑定 bootstrap 会话。
     *
     * <p>同一会话重试保持幂等；令牌一旦绑定，其他会话不能取得订单信息，防止链接预取和令牌泄露导致越权。</p>
     */
    public void exchangeToken(String bootstrapSessionId, QrTokenDigest presentedDigest, Instant now) {
        requirePreAcceptanceActive(now);
        String sessionId = required(bootstrapSessionId, "bootstrap 会话 ID");
        if (!tokenDigest.equals(Objects.requireNonNull(presentedDigest, "二维码令牌摘要不能为空"))) {
            throw new IllegalArgumentException("二维码令牌无效");
        }
        if (boundBootstrapSessionId == null) {
            boundBootstrapSessionId = sessionId;
            advance(now);
            return;
        }
        if (!boundBootstrapSessionId.equals(sessionId)) {
            throw new IllegalStateException("二维码令牌已被其他会话交换");
        }
    }

    /**
     * 记录 H5 首屏完成后的扫码动作。
     *
     * @param bootstrapSessionId 已绑定的 H5 bootstrap 会话
     * @param expectedVersion 客户端取得的订单版本，用于 CAS
     * @param now 操作时间
     */
    public void scan(String bootstrapSessionId, long expectedVersion, Instant now) {
        requireBoundSession(bootstrapSessionId);
        checkVersion(expectedVersion);
        requirePreAcceptanceActive(now);
        if (status == QrPayOrderStatus.SCANNED) {
            return;
        }
        if (status != QrPayOrderStatus.CREATED) {
            throw new IllegalStateException("二维码订单当前不可扫码");
        }
        status = QrPayOrderStatus.SCANNED;
        advance(now);
    }

    /**
     * 锁定付款方和资金来源账户，等待支付证明与风控校验。
     *
     * <p>在资金受理前阻止付款方等于收款方，避免自付订单进入后续统一交易入口。</p>
     */
    public void lockPayer(String payerUserId, String payerAccountId, long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        requirePreAcceptanceActive(now);
        String validPayerUserId = required(payerUserId, "付款用户 ID");
        String validPayerAccountId = required(payerAccountId, "付款账户 ID");
        if (payeeUserId.equals(validPayerUserId) || payeeAccountId.equals(validPayerAccountId)) {
            throw new IllegalStateException("不允许向本人付款");
        }
        if (status != QrPayOrderStatus.SCANNED) {
            throw new IllegalStateException("二维码订单当前不可锁定付款方");
        }
        this.payerUserId = validPayerUserId;
        this.payerAccountId = validPayerAccountId;
        status = QrPayOrderStatus.PENDING_CONFIRMATION;
        advance(now);
    }

    /** 将已确认订单转入人工风控复核；不会创建交易或冻结资金。 */
    public void markRiskReview(long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        requirePreAcceptanceActive(now);
        if (status != QrPayOrderStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("二维码订单当前不可转人工风控复核");
        }
        status = QrPayOrderStatus.RISK_REVIEW;
        advance(now);
    }

    /** 运营批准人工复核后将订单恢复到待确认，用户可重新确认；仅 RISK_REVIEW 可恢复。 */
    public void resumeFromRiskReview(long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        requirePreAcceptanceActive(now);
        if (status != QrPayOrderStatus.RISK_REVIEW) {
            throw new IllegalStateException("二维码订单当前不在人工复核状态");
        }
        status = QrPayOrderStatus.PENDING_CONFIRMATION;
        advance(now);
    }

    /** 在资金受理前取消订单；重复取消不增加版本。 */
    public void cancel(long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        requirePreAcceptanceActive(now);
        if (status == QrPayOrderStatus.CANCELLED) {
            return;
        }
        if (status != QrPayOrderStatus.CREATED && status != QrPayOrderStatus.SCANNED
                && status != QrPayOrderStatus.PENDING_CONFIRMATION && status != QrPayOrderStatus.RISK_REVIEW) {
            throw new IllegalStateException("二维码订单当前不可取消");
        }
        status = QrPayOrderStatus.CANCELLED;
        advance(now);
    }

    /**
     * 记录统一资金交易已经受理的关联关系。
     *
     * <p>该方法只允许阶段四统一交易端口的适配器调用，且不代表支付成功；当前阶段没有应用服务调用它。</p>
     */
    public void acceptByFundTransaction(long expectedVersion, String transactionId, Instant now) {
        checkVersion(expectedVersion);
        requirePreAcceptanceActive(now);
        if (status != QrPayOrderStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("二维码订单当前不可受理资金交易");
        }
        this.transactionId = required(transactionId, "统一交易 ID");
        status = QrPayOrderStatus.PROCESSING;
        advance(now);
    }

    /**
     * 将尚未受理资金的超时订单持久化为过期终态。
     *
     * <p>过期不是展示层推断：必须和其他状态变更一样写入版本列，防止并发会话在五分钟边界后继续交换或扫码。</p>
     * @return 本次是否发生状态变更
     */
    public boolean expireIfNecessary(Instant now) {
        if (!now.isBefore(expiresAt) && isPreAcceptanceState()) {
            status = QrPayOrderStatus.EXPIRED;
            advance(now);
            return true;
        }
        return false;
    }

    private void requireBoundSession(String bootstrapSessionId) {
        if (boundBootstrapSessionId == null || !boundBootstrapSessionId.equals(bootstrapSessionId)) {
            throw new IllegalStateException("H5 会话未绑定二维码订单");
        }
    }

    private void checkVersion(long expectedVersion) {
        if (version != expectedVersion) {
            throw new IllegalStateException("二维码订单版本已经变化");
        }
    }

    private void requirePreAcceptanceActive(Instant now) {
        if (expireIfNecessary(now)) {
            throw new IllegalStateException("二维码订单已过期");
        }
        if (status == QrPayOrderStatus.EXPIRED) {
            throw new IllegalStateException("二维码订单已过期");
        }
        if (!isPreAcceptanceState()) {
            throw new IllegalStateException("二维码订单当前不可继续受理");
        }
    }

    private boolean isPreAcceptanceState() {
        return status == QrPayOrderStatus.CREATED || status == QrPayOrderStatus.SCANNED
                || status == QrPayOrderStatus.PENDING_CONFIRMATION || status == QrPayOrderStatus.RISK_REVIEW;
    }

    private void advance(Instant now) {
        version++;
        updatedAt = now;
    }

    private static String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }

    public String getOrderId() { return orderId; }
    public String getPayeeUserId() { return payeeUserId; }
    public String getPayeeAccountId() { return payeeAccountId; }
    public long getAmountFen() { return amountFen; }
    public String getSubject() { return subject; }
    /** 返回持久化所需的令牌摘要，绝不返回原始令牌。 */
    public QrTokenDigest getTokenDigest() { return tokenDigest; }
    public QrPayOrderStatus getStatus() { return status; }
    public String getBoundBootstrapSessionId() { return boundBootstrapSessionId; }
    public String getPayerUserId() { return payerUserId; }
    public String getPayerAccountId() { return payerAccountId; }
    public String getTransactionId() { return transactionId; }
    public long getVersion() { return version; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
