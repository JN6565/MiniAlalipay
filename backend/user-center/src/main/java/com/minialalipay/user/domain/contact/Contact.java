package com.minialalipay.user.domain.contact;

import java.time.Instant;

/**
 * 联系人聚合根。
 *
 * <p>表示付款人成功转账后自动归档的常用收款人投影。
 * 联系人是单向关系——{@code ownerUserId} 将 {@code payeeUserId} 视为常用收款人，
 * 不代表好友或通讯录关系。</p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li>联合主键 {@code (ownerUserId, payeeUserId)} 在表内唯一</li>
 *   <li>{@code successCount} 只增不减，每次成功转账归档时递增</li>
 *   <li>{@code lastSuccessAt} 每次归档时更新为最新成功时间</li>
 *   <li>{@code hidden} 为 true 的联系人不出现在常用列表中</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.domain.contact.ContactRepository 联系人仓储接口
 */
public class Contact {

    /** 联系人列表所有者用户 ID（ULID 格式，26 位字符）。 */
    private final String ownerUserId;

    /** 成功收款的用户 ID（ULID 格式，26 位字符）。 */
    private final String payeeUserId;

    /** 付款人给收款人设置的备注别名（最大 64 字符，可空）。 */
    private String alias;

    /** 累计成功转账次数，只增不减。 */
    private long successCount;

    /** 最近一次成功转账时间（UTC，毫秒精度）。 */
    private Instant lastSuccessAt;

    /** 是否置顶。 */
    private boolean pinned;

    /** 是否隐藏（隐藏后不出现在常用收款人列表）。 */
    private boolean hidden;

    /** 版本号（乐观锁）。 */
    private long version;

    /** 创建时间（UTC，毫秒精度）。 */
    private final Instant createdAt;

    /** 最近更新时间（UTC，毫秒精度）。 */
    private Instant updatedAt;

    /**
     * 创建新联系人（首次归档时使用）。
     *
     * @param ownerUserId  联系人列表所有者
     * @param payeeUserId  收款用户
     * @param lastSuccessAt 最近成功转账时间
     */
    public Contact(String ownerUserId, String payeeUserId, Instant lastSuccessAt) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new IllegalArgumentException("联系人所有者不能为空");
        }
        if (payeeUserId == null || payeeUserId.isBlank()) {
            throw new IllegalArgumentException("收款用户 ID 不能为空");
        }
        this.ownerUserId = ownerUserId;
        this.payeeUserId = payeeUserId;
        this.successCount = 1;
        this.lastSuccessAt = lastSuccessAt;
        this.pinned = false;
        this.hidden = false;
        this.version = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 重建联系人对象（从数据库加载时使用）。
     */
    public Contact(
            String ownerUserId,
            String payeeUserId,
            String alias,
            long successCount,
            Instant lastSuccessAt,
            boolean pinned,
            boolean hidden,
            long version,
            Instant createdAt,
            Instant updatedAt
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

    /**
     * 递增成功次数并更新最近成功时间。
     *
     * <p>转账成功归档时调用，{@code successCount} 加 1，
     * {@code lastSuccessAt} 更新为当前时间。</p>
     */
    public void incrementSuccess() {
        this.successCount++;
        this.lastSuccessAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 更新备注别名。
     *
     * @param alias 新的备注别名（最大 64 字符，可空）
     */
    public void updateAlias(String alias) {
        this.alias = alias != null && alias.length() > 64 ? alias.substring(0, 64) : alias;
        this.updatedAt = Instant.now();
    }

    /**
     * 切换置顶状态。
     *
     * @param pinned 是否置顶
     */
    public void setPinned(boolean pinned) {
        this.pinned = pinned;
        this.updatedAt = Instant.now();
    }

    /**
     * 切换隐藏状态。
     *
     * @param hidden 是否隐藏
     */
    public void setHidden(boolean hidden) {
        this.hidden = hidden;
        this.updatedAt = Instant.now();
    }

    // ==================== Getters ====================

    public String getOwnerUserId() { return ownerUserId; }
    public String getPayeeUserId() { return payeeUserId; }
    public String getAlias() { return alias; }
    public long getSuccessCount() { return successCount; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public boolean isPinned() { return pinned; }
    public boolean isHidden() { return hidden; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
