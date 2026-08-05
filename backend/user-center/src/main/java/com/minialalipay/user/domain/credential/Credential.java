package com.minialalipay.user.domain.credential;

import java.time.Instant;

/**
 * 用户凭证实体。
 *
 * <p>保存登录密码、支付密码的强哈希以及两套独立的失败锁定状态。
 * 每个用户对应一条凭证记录，通过 {@code user_id} 与用户关联。</p>
 *
 * <p>密码安全规则：
 * <ul>
 *   <li>登录密码使用 BCrypt 或 Argon2id 强哈希算法，不存储明文</li>
 *   <li>支付密码为独立的 6 位数字密码，使用相同哈希算法</li>
 *   <li>支付密码修改后立即废弃所有已签发的支付证明（{@code payment_proof}）</li>
 * </ul>
 * </p>
 *
 * <p>锁定机制：
 * <ul>
 *   <li>登录密码连续失败 5 次后锁定 30 分钟</li>
 *   <li>支付密码连续失败 5 次后锁定 30 分钟</li>
 *   <li>两套锁定状态独立，互不影响</li>
 *   <li>锁定期间的登录/支付请求直接返回 {@link com.minialalipay.user.domain.auth.UserErrorCode#LOGIN_LOCKED}
 *       或 {@link com.minialalipay.user.domain.auth.UserErrorCode#PAYMENT_LOCKED}</li>
 * </ul>
 * </p>
 *
 * @see com.minialalipay.user.domain.user.User 用户聚合根
 * @see com.minialalipay.user.infrastructure.security.PasswordHasher 密码哈希工具
 */
public class Credential {

    /**
     * 用户 ID（与 {@code app_user} 表一对一关联）。
     * <p>作为主键和外键，保证每个用户只有一条凭证记录。</p>
     */
    private final String userId;

    /**
     * 登录密码哈希（BCrypt 或 Argon2id 格式）。
     * <p>注册时设置，修改登录密码时更新。</p>
     */
    private String loginPasswordHash;

    /**
     * 支付密码哈希（BCrypt 或 Argon2id 格式，可空）。
     * <p>用户首次设置支付密码时填充，未设置时为 null。
     * 支付密码为独立的 6 位数字密码，与登录密码无关。</p>
     */
    private String paymentPasswordHash;

    /**
     * 连续登录密码失败次数。
     * <p>登录成功时重置为 0，失败时递增。
     * 达到 5 次时触发锁定（设置 {@code loginLockUntil}）。</p>
     */
    private int loginFailCount;

    /**
     * 连续支付密码失败次数。
     * <p>支付密码校验成功时重置为 0，失败时递增。
     * 达到 5 次时触发锁定（设置 {@code payLockUntil}）。</p>
     */
    private int payFailCount;

    /**
     * 登录锁定截止时间（UTC，毫秒精度，可空）。
     * <p>不为 null 时表示登录已被锁定，在此时间之前不允许登录。
     * 锁定时间 = 最后一次失败时间 + 30 分钟。</p>
     */
    private Instant loginLockUntil;

    /**
     * 支付验证锁定截止时间（UTC，毫秒精度，可空）。
     * <p>不为 null 时表示支付密码校验已被锁定，在此时间之前不允许校验。
     * 锁定时间 = 最后一次失败时间 + 30 分钟。</p>
     */
    private Instant payLockUntil;

    /**
     * 支付密码世代号。
     * <p>用于废弃旧授权：每次修改支付密码时递增，
     * 签发和消费支付证明时必须校验版本号一致。</p>
     */
    private long payPasswordVersion;

    /**
     * 版本号（乐观锁）。
     * <p>用于并发控制，每次凭证状态变更时递增。</p>
     */
    private long version;

    /**
     * 最近密码、失败次数或锁定状态更新时间（UTC，毫秒精度）。
     */
    private Instant updatedAt;

    /**
     * 创建新凭证（注册时使用）。
     *
     * <p>注册时只设置登录密码哈希，支付密码哈希为 null（待用户后续设置）。</p>
     *
     * @param userId            用户 ID
     * @param loginPasswordHash 登录密码哈希（BCrypt 或 Argon2id 格式）
     * @throws IllegalArgumentException 如果用户 ID 或密码哈希为空
     */
    public Credential(String userId, String loginPasswordHash) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        if (loginPasswordHash == null || loginPasswordHash.isBlank()) {
            throw new IllegalArgumentException("登录密码哈希不能为空");
        }

        this.userId = userId;
        this.loginPasswordHash = loginPasswordHash;
        this.paymentPasswordHash = null;
        this.loginFailCount = 0;
        this.payFailCount = 0;
        this.loginLockUntil = null;
        this.payLockUntil = null;
        this.payPasswordVersion = 0;
        this.version = 0;
        this.updatedAt = Instant.now();
    }

    /**
     * 重建凭证对象（从数据库加载时使用）。
     *
     * <p>此构造函数用于从数据库加载已有凭证，不执行业务校验。</p>
     */
    public Credential(
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

    /**
     * 记录登录失败。
     *
     * <p>失败次数递增，如果达到 5 次则锁定 30 分钟。
     * 锁定期间的登录请求直接返回 {@link com.minialalipay.user.domain.auth.UserErrorCode#LOGIN_LOCKED}。</p>
     *
     * @return 如果触发锁定则返回 true
     */
    public boolean recordLoginFailure() {
        this.loginFailCount++;
        this.updatedAt = Instant.now();

        if (this.loginFailCount >= 5) {
            this.loginLockUntil = Instant.now().plusSeconds(30 * 60);
            return true;
        }
        return false;
    }

    /**
     * 重置登录失败计数。
     *
     * <p>登录成功时调用，将失败次数重置为 0，清除锁定时间。</p>
     */
    public void resetLoginFailCount() {
        this.loginFailCount = 0;
        this.loginLockUntil = null;
        this.updatedAt = Instant.now();
    }

    /**
     * 检查登录是否被锁定。
     *
     * <p>如果 {@code loginLockUntil} 不为 null 且当前时间在其之前，
     * 则表示登录已被锁定。</p>
     *
     * @return 如果登录被锁定则返回 true
     */
    public boolean isLoginLocked() {
        if (this.loginLockUntil == null) {
            return false;
        }
        return Instant.now().isBefore(this.loginLockUntil);
    }

    /**
     * 检查支付密码是否已设置。
     *
     * @return 如果支付密码哈希不为 null 则返回 true
     */
    public boolean isPaymentPasswordSet() {
        return this.paymentPasswordHash != null;
    }

    // ==================== Getters ====================

    public String getUserId() { return userId; }
    public String getLoginPasswordHash() { return loginPasswordHash; }
    public String getPaymentPasswordHash() { return paymentPasswordHash; }
    public int getLoginFailCount() { return loginFailCount; }
    public int getPayFailCount() { return payFailCount; }
    public Instant getLoginLockUntil() { return loginLockUntil; }
    public Instant getPayLockUntil() { return payLockUntil; }
    public long getPayPasswordVersion() { return payPasswordVersion; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    // ==================== Setters (for persistence) ====================

    public void setLoginPasswordHash(String loginPasswordHash) { this.loginPasswordHash = loginPasswordHash; }
    public void setPaymentPasswordHash(String paymentPasswordHash) { this.paymentPasswordHash = paymentPasswordHash; }
    public void setPayFailCount(int payFailCount) { this.payFailCount = payFailCount; }
    public void setPayLockUntil(Instant payLockUntil) { this.payLockUntil = payLockUntil; }
    public void setPayPasswordVersion(long payPasswordVersion) { this.payPasswordVersion = payPasswordVersion; }
    public void setVersion(long version) { this.version = version; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
