package com.minialalipay.account.infrastructure.credit.po;

import java.time.Instant;

/**
 * 信用账户持久化对象，对应 {@code account_db.credit_account} 表。
 *
 * <p>该表记录用户的信用账户主信息，包括总额度、已用额度、冻结额度及账户状态等，
 * 是信用支付体系的核心主数据表。
 */
public class CreditAccountPO {

    /** 信用账户 ID，对应 CHAR(26) */
    private String creditAccountId;

    /** 用户 ID，对应 CHAR(26) */
    private String userId;

    /** 总额度（分），对应 BIGINT UNSIGNED */
    private Long totalLimitFen;

    /** 已用额度（分），对应 BIGINT UNSIGNED */
    private Long usedFen;

    /** 冻结额度（分），对应 BIGINT UNSIGNED */
    private Long frozenFen;

    /** 账户状态，对应 CHAR */
    private String status;

    /** 挂起原因，对应 VARCHAR */
    private String suspendReason;

    /** 用户显式开通 Mini 花呗的时间；为空表示仅预创建、尚未开通。 */
    private Instant openedAt;

    /** 乐观锁版本号，对应 BIGINT UNSIGNED */
    private Long version;

    /** 创建时间，对应 DATETIME(3) */
    private Instant createdAt;

    /** 更新时间，对应 DATETIME(3) */
    private Instant updatedAt;

    /** 无参构造器 */
    public CreditAccountPO() {
    }

    /** 全参数构造器 */
    public CreditAccountPO(String creditAccountId, String userId, Long totalLimitFen, Long usedFen,
                           Long frozenFen, String status, String suspendReason, Long version,
                           Instant createdAt, Instant updatedAt) {
        this.creditAccountId = creditAccountId;
        this.userId = userId;
        this.totalLimitFen = totalLimitFen;
        this.usedFen = usedFen;
        this.frozenFen = frozenFen;
        this.status = status;
        this.suspendReason = suspendReason;
        this.openedAt = createdAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getCreditAccountId() {
        return creditAccountId;
    }

    public void setCreditAccountId(String creditAccountId) {
        this.creditAccountId = creditAccountId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getTotalLimitFen() {
        return totalLimitFen;
    }

    public void setTotalLimitFen(Long totalLimitFen) {
        this.totalLimitFen = totalLimitFen;
    }

    public Long getUsedFen() {
        return usedFen;
    }

    public void setUsedFen(Long usedFen) {
        this.usedFen = usedFen;
    }

    public Long getFrozenFen() {
        return frozenFen;
    }

    public void setFrozenFen(Long frozenFen) {
        this.frozenFen = frozenFen;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSuspendReason() {
        return suspendReason;
    }

    public void setSuspendReason(String suspendReason) {
        this.suspendReason = suspendReason;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public void setOpenedAt(Instant openedAt) {
        this.openedAt = openedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
