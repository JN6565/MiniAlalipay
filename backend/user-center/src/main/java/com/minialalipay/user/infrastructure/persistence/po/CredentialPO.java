package com.minialalipay.user.infrastructure.persistence.po;

import java.time.Instant;

/**
 * 凭证持久化对象（PO）。
 *
 * <p>与 {@code credential} 表结构一一对应，用于 MyBatis 数据库操作。
 * PO 对象只在基础设施层使用，不得暴露到领域层或接口层。</p>
 *
 * <p>字段映射规则：
 * <ul>
 *   <li>数据库字段使用 {@code snake_case} 命名，Java 字段使用 {@code camelCase} 命名</li>
 *   <li>MyBatis 配置 {@code map-underscore-to-camel-case=true} 自动转换</li>
 *   <li>时间字段使用 {@code java.time.Instant}，对应数据库 {@code DATETIME(3)}</li>
 *   <li>金额字段使用 {@code long}（整数分），对应数据库 {@code BIGINT UNSIGNED}</li>
 * </ul>
 * </p>
 *
 * <p>与领域模型的转换：
 * <ul>
 *   <li>{@code CredentialPO} → {@code Credential}：从数据库加载时，由 Repository 实现转换</li>
 *   <li>{@code Credential} → {@code CredentialPO}：保存到数据库时，由 Repository 实现转换</li>
 *   <li>转换逻辑集中在 Repository 实现中，避免分散到各处</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.domain.credential.Credential 凭证领域模型
 * @see CredentialMapper 凭证 Mapper 接口
 */
public class CredentialPO {

    /**
     * 用户 ID（与 {@code app_user} 表一对一关联）。
     * <p>作为主键和外键，保证每个用户只有一条凭证记录，对应数据库字段 {@code user_id}。</p>
     */
    private String userId;

    /**
     * 登录密码哈希（BCrypt 或 Argon2id 格式）。
     * <p>注册时设置，修改登录密码时更新，对应数据库字段 {@code login_password_hash}。</p>
     */
    private String loginPasswordHash;

    /**
     * 支付密码哈希（BCrypt 或 Argon2id 格式，可空）。
     * <p>用户首次设置支付密码时填充，未设置时为 null，对应数据库字段 {@code payment_password_hash}。</p>
     */
    private String paymentPasswordHash;

    /**
     * 连续登录密码失败次数。
     * <p>登录成功时重置为 0，失败时递增，对应数据库字段 {@code login_fail_count}。</p>
     */
    private int loginFailCount;

    /**
     * 连续支付密码失败次数。
     * <p>支付密码校验成功时重置为 0，失败时递增，对应数据库字段 {@code pay_fail_count}。</p>
     */
    private int payFailCount;

    /**
     * 登录锁定截止时间（UTC，毫秒精度，可空）。
     * <p>不为 null 时表示登录已被锁定，对应数据库字段 {@code login_lock_until}。</p>
     */
    private Instant loginLockUntil;

    /**
     * 支付验证锁定截止时间（UTC，毫秒精度，可空）。
     * <p>不为 null 时表示支付密码校验已被锁定，对应数据库字段 {@code pay_lock_until}。</p>
     */
    private Instant payLockUntil;

    /**
     * 支付密码世代号。
     * <p>用于废弃旧授权，每次修改支付密码时递增，对应数据库字段 {@code pay_password_version}。</p>
     */
    private long payPasswordVersion;

    /**
     * 版本号（乐观锁）。
     * <p>用于并发控制，每次凭证状态变更时递增，对应数据库字段 {@code version}。</p>
     */
    private long version;

    /**
     * 最近密码、失败次数或锁定状态更新时间（UTC，毫秒精度）。
     * <p>对应数据库字段 {@code updated_at}。</p>
     */
    private Instant updatedAt;

    /**
     * 默认构造函数（MyBatis 反射需要）。
     */
    public CredentialPO() {
    }

    /**
     * 全参数构造函数（用于测试或手动构建）。
     */
    public CredentialPO(
            String userId,
            String loginPasswordHash,
            String paymentPasswordHash,
            int loginFailCount,
            int payFailCount,
            Instant loginLockUntil,
            Instant payLockUntil,
            long payPasswordVersion,
            long version,
            Instant updatedAt
    ) {
        this.userId = userId;
        this.loginPasswordHash = loginPasswordHash;
        this.paymentPasswordHash = paymentPasswordHash;
        this.loginFailCount = loginFailCount;
        this.payFailCount = payFailCount;
        this.loginLockUntil = loginLockUntil;
        this.payLockUntil = payLockUntil;
        this.payPasswordVersion = payPasswordVersion;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    // ==================== Getters and Setters ====================

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getLoginPasswordHash() {
        return loginPasswordHash;
    }

    public void setLoginPasswordHash(String loginPasswordHash) {
        this.loginPasswordHash = loginPasswordHash;
    }

    public String getPaymentPasswordHash() {
        return paymentPasswordHash;
    }

    public void setPaymentPasswordHash(String paymentPasswordHash) {
        this.paymentPasswordHash = paymentPasswordHash;
    }

    public int getLoginFailCount() {
        return loginFailCount;
    }

    public void setLoginFailCount(int loginFailCount) {
        this.loginFailCount = loginFailCount;
    }

    public int getPayFailCount() {
        return payFailCount;
    }

    public void setPayFailCount(int payFailCount) {
        this.payFailCount = payFailCount;
    }

    public Instant getLoginLockUntil() {
        return loginLockUntil;
    }

    public void setLoginLockUntil(Instant loginLockUntil) {
        this.loginLockUntil = loginLockUntil;
    }

    public Instant getPayLockUntil() {
        return payLockUntil;
    }

    public void setPayLockUntil(Instant payLockUntil) {
        this.payLockUntil = payLockUntil;
    }

    public long getPayPasswordVersion() {
        return payPasswordVersion;
    }

    public void setPayPasswordVersion(long payPasswordVersion) {
        this.payPasswordVersion = payPasswordVersion;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
