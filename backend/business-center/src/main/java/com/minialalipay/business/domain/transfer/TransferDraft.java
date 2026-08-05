package com.minialalipay.business.domain.transfer;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * 转账草稿聚合，保存服务端解析的双方账户并保护金额、版本和生命周期约束。
 */
public final class TransferDraft {
    /** 普通转账金额上限，单位分。 */
    public static final long MAX_AMOUNT_FEN = 5_000_000L;
    private static final Duration VALIDITY = Duration.ofMinutes(30);

    private final String draftId;
    private final String payerUserId;
    private final String payeeUserId;
    private final String payerAccountId;
    private final String payeeAccountId;
    private long amountFen;
    private String remark;
    private DraftStatus status;
    private long version;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant updatedAt;

    /** 创建一份 30 分钟有效的服务端草稿。 */
    public static TransferDraft create(String draftId, String payerUserId, String payeeUserId,
                                       String payerAccountId, String payeeAccountId, long amountFen,
                                       String remark, Instant now) {
        return new TransferDraft(draftId, payerUserId, payeeUserId, payerAccountId, payeeAccountId,
                amountFen, remark, DraftStatus.DRAFT, 0L, now.plus(VALIDITY), now, now);
    }

    /** 从持久化事实重建草稿。 */
    public TransferDraft(String draftId, String payerUserId, String payeeUserId, String payerAccountId,
                         String payeeAccountId, long amountFen, String remark, DraftStatus status,
                         long version, Instant expiresAt, Instant createdAt, Instant updatedAt) {
        this.draftId = required(draftId, "草稿 ID");
        this.payerUserId = required(payerUserId, "付款用户 ID");
        this.payeeUserId = required(payeeUserId, "收款用户 ID");
        this.payerAccountId = required(payerAccountId, "付款账户 ID");
        this.payeeAccountId = required(payeeAccountId, "收款账户 ID");
        if (payerUserId.equals(payeeUserId) || payerAccountId.equals(payeeAccountId)) {
            throw new IllegalArgumentException("不允许向本人账户付款");
        }
        requireAmount(amountFen);
        requireRemark(remark);
        this.amountFen = amountFen;
        this.remark = remark;
        this.status = Objects.requireNonNull(status, "草稿状态不能为空");
        if (version < 0) throw new IllegalArgumentException("草稿版本不得为负");
        this.version = version;
        this.expiresAt = Objects.requireNonNull(expiresAt, "过期时间不能为空");
        this.createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        this.updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
    }

    /** 按客户端读取版本编辑草稿；字段变化使旧确认上下文失效。 */
    public void edit(long expectedVersion, long newAmountFen, String newRemark, Instant now) {
        checkVersion(expectedVersion);
        requireActive(now);
        if (status == DraftStatus.SUBMITTED || status == DraftStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("交易草稿当前不可编辑");
        }
        requireAmount(newAmountFen);
        requireRemark(newRemark);
        amountFen = newAmountFen;
        remark = newRemark;
        status = DraftStatus.DRAFT;
        version++;
        updatedAt = now;
    }

    /** 校验草稿并推进到已校验状态。 */
    public void validate(long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        requireActive(now);
        if (status != DraftStatus.DRAFT && status != DraftStatus.VALIDATED) {
            throw new IllegalStateException("交易草稿当前不可校验");
        }
        status = DraftStatus.VALIDATED;
        version++;
        updatedAt = now;
    }

    /** 锁定已校验草稿，等待可信界面提交。 */
    public void awaitConfirmation(long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        requireActive(now);
        if (status != DraftStatus.VALIDATED && status != DraftStatus.PENDING_CONFIRMATION) {
            throw new IllegalStateException("交易草稿尚未通过校验");
        }
        if (status == DraftStatus.VALIDATED) {
            status = DraftStatus.PENDING_CONFIRMATION;
            version++;
            updatedAt = now;
        }
    }

    /** 将草稿原子标记为已提交。 */
    public void submit(long expectedVersion, Instant now) {
        checkVersion(expectedVersion);
        requireActive(now);
        if (status != DraftStatus.PENDING_CONFIRMATION) throw new IllegalStateException("交易草稿当前不可提交");
        status = DraftStatus.SUBMITTED;
        version++;
        updatedAt = now;
    }

    private void checkVersion(long expectedVersion) {
        if (version != expectedVersion) throw new IllegalStateException("资源版本已经变化");
    }
    private void requireActive(Instant now) {
        if (!now.isBefore(expiresAt)) {
            status = DraftStatus.EXPIRED;
            throw new IllegalStateException("交易草稿已过期");
        }
    }
    private static void requireAmount(long amountFen) {
        if (amountFen < 1 || amountFen > MAX_AMOUNT_FEN) throw new IllegalArgumentException("金额超出允许范围");
    }
    private static void requireRemark(String remark) {
        if (remark != null && remark.length() > 128) throw new IllegalArgumentException("转账备注不得超过 128 个字符");
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + "不能为空");
        return value;
    }

    public String getDraftId() { return draftId; }
    public String getPayerUserId() { return payerUserId; }
    public String getPayeeUserId() { return payeeUserId; }
    public String getPayerAccountId() { return payerAccountId; }
    public String getPayeeAccountId() { return payeeAccountId; }
    public long getAmountFen() { return amountFen; }
    public String getRemark() { return remark; }
    public DraftStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
