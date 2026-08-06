package com.minialalipay.user.infrastructure.persistence.po;

import java.time.Instant;

/**
 * 联系人持久化对象（PO）。
 *
 * <p>与 {@code contact} 表结构一一对应，用于 MyBatis 数据库操作。
 * PO 对象只在基础设施层使用，不得暴露到领域层或接口层。</p>
 */
public class ContactPO {

    private String ownerUserId;
    private String payeeUserId;
    private String alias;
    private long successCount;
    private Instant lastSuccessAt;
    private boolean pinned;
    private boolean hidden;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    /** 默认构造函数（MyBatis 反射需要）。 */
    public ContactPO() {}

    public ContactPO(
            String ownerUserId, String payeeUserId, String alias,
            long successCount, Instant lastSuccessAt,
            boolean pinned, boolean hidden, long version,
            Instant createdAt, Instant updatedAt
    ) {
        this.ownerUserId = ownerUserId;
        this.payeeUserId = payeeUserId;
        this.alias = alias;
        this.successCount = successCount;
        this.lastSuccessAt = lastSuccessAt;
        this.pinned = pinned;
        this.hidden = hidden;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== Getters and Setters ====================

    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getPayeeUserId() { return payeeUserId; }
    public void setPayeeUserId(String payeeUserId) { this.payeeUserId = payeeUserId; }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }

    public long getSuccessCount() { return successCount; }
    public void setSuccessCount(long successCount) { this.successCount = successCount; }

    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public void setLastSuccessAt(Instant lastSuccessAt) { this.lastSuccessAt = lastSuccessAt; }

    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
